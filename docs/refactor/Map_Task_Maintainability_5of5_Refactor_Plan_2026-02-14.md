# Map_Task_Maintainability_5of5_Refactor_Plan_2026-02-14.md

## Purpose

Raise map/task maintainability and change safety from current `3.9 / 5` to
`5.0 / 5` without regressing architecture compliance, determinism, or release
stability.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: Map/Task Maintainability 5/5 Refactor
- Owner: XCPro Team
- Date: 2026-02-14
- Issue/PR: TBD
- Status: In progress

Compliance correction (2026-02-18):
- The structural decomposition work in this plan remains valid.
- Deep-pass runtime/file-flow audit found unresolved map/task slice risks
  (import fidelity, CSV contract safety, main-thread task file I/O, target lock semantics).
- Latest deep-pass refresh (2026-02-18, pass #6) added additional unresolved
  blockers (export/share exception hardening, AAT target autosave gap,
  duplicate-waypoint ID target-state aliasing, CUP role-code contract mismatch).
- Hardening implementation pass #1 (2026-02-18) has now closed a subset of those
  blockers (URI-driven CUP import, CSV contract parser/writer alignment, UTF-8 IO,
  exception hardening in task-files VM flows, active-leg retention, duplicate-id
  target-state isolation, AAT target autosave).
- Hardening implementation pass #2 (2026-02-18) closed additional blockers:
  - canonical export/share now serializes real target snapshots,
  - serializer now preserves/restores task id + waypoint custom/OZ payloads,
  - legacy duplicate QR path removed in favor of canonical dialog flow,
  - sparse target snapshot serialization bug fixed (`index`/`id` resolution).
- Hardening implementation pass #3 (2026-02-18) closed additional blockers:
  - deterministic fallback ID policy now replaces random IDs in persistence adapters,
  - CUP storage partitioning is now task-type scoped (`cup_tasks/racing`, `cup_tasks/aat`) with legacy fallback reads,
  - share flow now emits a single multi-document chooser request (no duplicate chooser events).
- Hardening implementation pass #4 (2026-02-18) added failure-mode confidence:
  - `TaskFilesRepository` write/read failure handling tests,
  - racing/AAT CUP storage failure-path tests,
  - `TaskFilesViewModel` share failure mapping tests.
- Hardening implementation pass #5 (2026-02-18) added migration policy coverage:
  - one-time legacy `cup_tasks` -> scoped storage migration in racing/AAT storage paths,
  - migration behavior verified by partitioning tests with legacy/scoped assertions.
- Hardening implementation pass #6 (2026-02-18) added device-level share confidence:
  - new instrumentation coverage for canonical multi-document share dispatch
    (`TaskFilesShareInstrumentedTest`),
  - connected-device run validates single chooser + `ACTION_SEND_MULTIPLE` share behavior.
- Hardening implementation pass #7 (2026-02-18) added rollout cleanup closure:
  - residual legacy `cup_tasks` conflicts are now archived by strict cleanup policy
    once both racing/AAT migrations are complete,
  - racing/AAT storage paths invoke cleanup policy on migration and post-migration access,
  - partition/failure tests now lock cleanup sequencing and deterministic prefs isolation.
- Deep-pass refresh (2026-02-18, pass #11) status:
  - task-files hardening gate remains release-ready after re-audit,
  - residual non-blocking debt was identified for immediate follow-up.
- Hardening implementation pass #12 (2026-02-18) closed the pass #11 follow-up backlog:
  - `TaskManagerCoordinator.calculateTaskDistanceForTask(task)` now honors provided task waypoint input,
  - task map renderer `printStackTrace()` calls replaced with bounded debug-only logging.
- Hardening implementation pass #13 (2026-02-18) closed additional residual projection/import drift:
  - `TaskRepository` now preserves explicit waypoint roles and OZ metadata in domain projection,
  - AAT target-capable projection now includes `OPTIONAL` role in addition to `TURNPOINT`,
  - `TaskSheetViewModel` persisted import now batches waypoint adds without nested mutate/sync churn.
- Verification rerun after pass #12:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskStoragePartitioningTest" --tests "com.example.xcpro.tasks.TaskStorageFailureModesTest"`: PASS
  - `./gradlew --% :app:connectedDebugAndroidTest --no-parallel -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.class=com.example.xcpro.TaskFilesShareInstrumentedTest`: PASS
  - `./gradlew connectedDebugAndroidTest --no-parallel`: PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskManagerCoordinatorTest"`: PASS
  - `./gradlew enforceRules`: PASS
  - `./gradlew testDebugUnitTest`: PASS
  - `./gradlew assembleDebug`: PASS
  - `./gradlew enforceRules testDebugUnitTest assembleDebug`: PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskSheetViewModelImportTest" --tests "com.example.xcpro.tasks.domain.TaskRepositoryProjectionComplianceTest"`: PASS
- Map/task files hardening items in
  `docs/ARCHITECTURE/CHANGE_PLAN_MAP_TASK_SLICE_HARDENING_2026-02-18.md`
  are now completed and re-verified for release-path confidence.
- Deep-pass refresh (2026-02-22, SI compliance re-pass #5) found a critical
  AAT geometry contract defect:
  - AAT start/finish cylinder UI + persistence contract is radius.
  - runtime render/geometry/validation paths still divide that radius by 2.
  - impact: rendered/validated cylinder size is half the configured value.
- Hardening implementation pass #14 (planned) will close this defect:
  - preserve radius authority from `AATRadiusAuthority` / point configurators.
  - remove radius-halving (`/ 2.0`) in:
    - `feature/map/src/main/java/com/example/xcpro/tasks/aat/rendering/AATTaskRenderer.kt`
    - `feature/map/src/main/java/com/example/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt`
    - `feature/map/src/main/java/com/example/xcpro/tasks/aat/validation/AATValidationBridge.kt`
  - lock parity with focused JVM coverage and map/task integration checks.
- Tracking docs:
  - `docs/UNITS/SI_REPASS_FINDINGS_2026-02-22.md`
  - `docs/UNITS/CHANGE_PLAN_SI_UNITS_COMPLIANCE_2026-02-22.md`

Execution update (2026-02-14):

- Phase 0 baseline/contract pass completed:
  - plan created and architecture execution pointers updated.
- Phase 1 decomposition pass completed (initial tranche):
  - `MapCameraManager.kt` reduced from 403 -> 316 LOC.
  - `MapScreenReplayCoordinator.kt` reduced from 514 -> 328 LOC.
  - `LocationManager.kt` reduced from 399 -> 310 LOC.
  - `BlueLocationOverlay.kt` reduced from 356 -> 194 LOC.
  - `RacingNavigationEngine.kt` reduced from 510 -> 289 LOC.
  - `AATTaskManager.kt` reduced from 418 -> 292 LOC.
  - `RacingTaskManager.kt` reduced from 407 -> 269 LOC.
  - extracted focused collaborators:
    - `MapCameraEffects.kt`
    - `DisplayPoseRenderCoordinator.kt`
    - `SailplaneIconBitmapFactory.kt`
    - `MapReplaySnapshotControllers.kt`
    - `RacingReplayTaskHelpers.kt`
    - `RacingNavigationEngineSupport.kt`
- Phase 2 guardrail add-on completed (initial):
  - `scripts/ci/enforce_rules.ps1` now enforces line budgets for:
    - `MapCameraManager.kt` (`<= 350`)
    - `MapScreenReplayCoordinator.kt` (`<= 350`)
    - `MapScreenViewModel.kt` (`<= 350`)
    - `LocationManager.kt` (`<= 350`)
    - `BlueLocationOverlay.kt` (`<= 350`)
    - `RacingNavigationEngine.kt` (`<= 350`)
    - `AATTaskManager.kt` (`<= 350`)
    - `RacingTaskManager.kt` (`<= 350`)
- Phase 3 decomposition pass completed (AAT/task UI tranche):
  - `RulesBTTab.kt` reduced from 492 -> 69 LOC.
  - `AATDistanceCalculator.kt` reduced from 489 -> 308 LOC.
  - `AATTaskDisplay.kt` reduced from 477 -> 276 LOC.
  - `AATTaskCalculator.kt` reduced from 474 -> 173 LOC.
  - `AATTaskValidator.kt` reduced from 452 -> 53 LOC.
  - extracted focused collaborators/models:
    - `RulesBTTabComponents.kt`
    - `RulesBTTabParameters.kt`
    - `AATInteractiveDistanceCalculator.kt`
    - `AATTaskDisplayGeometryBuilder.kt`
    - `AATTaskDisplayModels.kt`
    - `AATTaskCalculatorModels.kt`
    - `AATTaskPerformanceSupport.kt`
    - `AATTaskQuickValidationEngine.kt`
- Phase 3 decomposition pass completed (map action UI tranche):
  - `MapActionButtons.kt` reduced from 464 -> 162 LOC.
  - extracted focused collaborator:
    - `MapActionButtonItems.kt` (312 LOC)
- Phase 3 decomposition pass completed (map/task UI and runtime tranche 2):
  - `AATEditModeOverlay.kt` reduced from 415 -> 101 LOC.
  - `SectorAreaCalculator.kt` reduced from 408 -> 160 LOC.
  - `RacingReplayLogBuilder.kt` reduced from 406 -> 157 LOC.
  - `RacingTask.kt` reduced from 400 -> 299 LOC.
  - `SnailTrailOverlay.kt` reduced from 396 -> 310 LOC.
  - `AATManageList.kt` reduced from 395 -> 97 LOC.
  - `RacingWaypointList.kt` reduced from 394 -> 115 LOC.
  - extracted focused collaborators/models:
    - `AATEditModeHeader.kt`
    - `AATEditModeInfoCard.kt`
    - `AATEditModeActions.kt`
    - `SectorAreaGeometrySupport.kt`
    - `RacingReplayAnchorBuilder.kt`
    - `SnailTrailTailRenderer.kt`
    - `AATManageListItems.kt`
    - `RacingWaypointListItems.kt`
    - `RacingTaskValidationModels.kt`
    - `RacingTaskResultModels.kt`
- Phase 2 guardrail add-on completed (tranche 2):
  - `scripts/ci/enforce_rules.ps1` now also enforces line budgets for:
    - `RulesBTTab.kt` (`<= 350`)
    - `RulesBTTabComponents.kt` (`<= 350`)
    - `RulesBTTabParameters.kt` (`<= 350`)
    - `AATDistanceCalculator.kt` (`<= 350`)
    - `AATTaskDisplay.kt` (`<= 350`)
    - `AATTaskCalculator.kt` (`<= 350`)
    - `AATTaskValidator.kt` (`<= 350`)
    - `AATTaskQuickValidationEngine.kt` (`<= 350`)
    - `AATInteractiveDistanceCalculator.kt` (`<= 350`)
    - `MapActionButtons.kt` (`<= 350`)
    - `MapActionButtonItems.kt` (`<= 350`)
    - `AATEditModeOverlay.kt` (`<= 350`)
    - `AATEditModeHeader.kt` (`<= 350`)
    - `AATEditModeInfoCard.kt` (`<= 350`)
    - `AATEditModeActions.kt` (`<= 350`)
    - `SectorAreaCalculator.kt` (`<= 350`)
    - `SectorAreaGeometrySupport.kt` (`<= 350`)
    - `RacingReplayLogBuilder.kt` (`<= 350`)
    - `RacingReplayAnchorBuilder.kt` (`<= 350`)
    - `RacingTask.kt` (`<= 350`)
    - `RacingTaskValidationModels.kt` (`<= 350`)
    - `RacingTaskResultModels.kt` (`<= 350`)
    - `SnailTrailOverlay.kt` (`<= 350`)
    - `SnailTrailTailRenderer.kt` (`<= 350`)
    - `AATManageList.kt` (`<= 350`)
    - `AATManageListItems.kt` (`<= 350`)
    - `RacingWaypointList.kt` (`<= 350`)
    - `RacingWaypointListItems.kt` (`<= 350`)
- Phase 3 decomposition pass completed (task runtime/map geometry tranche 3):
  - `AATPathOptimizer.kt` reduced from 371 -> 342 LOC.
  - `AATMapInteractionHandler.kt` reduced from 427 -> 277 LOC.
  - `AATMovablePointManager.kt` reduced from 429 -> 96 LOC.
  - `AATMapVisualIndicators.kt` reduced from 403 -> 168 LOC.
  - `AATMapRenderer.kt` reduced from 383 -> 278 LOC.
  - `FAIComplianceRules.kt` reduced from 415 -> 97 LOC.
  - `AATWaypointManager.kt` reduced from 420 -> 34 LOC.
  - `RacingBoundaryCrossingPlanner.kt` reduced from 427 -> 231 LOC.
  - `KeyholeGeometry.kt` reduced from 380 -> 202 LOC.
  - `AATInteractiveTurnpointManager.kt` reduced from 368 -> 220 LOC.
  - `DefaultAATTaskEngine.kt` reduced from 370 -> 215 LOC.
  - `AATManageListItems.kt` reduced from 354 -> 313 LOC.
  - extracted focused collaborators/models:
    - `AATPathOptimizerSupport.kt`
    - `AATAreaTapDetector.kt`
    - `AATMovablePointGeometrySupport.kt`
    - `AATMovablePointStrategySupport.kt`
    - `AATMapVisualStatusIndicators.kt`
    - `AATInteractiveTurnpointIntegration.kt`
    - `AATTargetPointPinRenderer.kt`
    - `FAIComplianceTaskRules.kt`
    - `FAIComplianceAreaRules.kt`
    - `AATWaypointInitializationSupport.kt`
    - `AATWaypointMutationSupport.kt`
    - `AATTaskWaypointCodec.kt`
    - `RacingBoundaryCrossingMath.kt`
    - `KeyholeShapeSupport.kt`
    - `AATManageListTypeInference.kt`
- Phase 2 guardrail add-on completed (tranche 3):
  - `scripts/ci/enforce_rules.ps1` now also enforces line budgets for:
    - `AATPathOptimizer.kt`, `AATPathOptimizerSupport.kt`
    - `AATInteractiveTurnpointManager.kt`, `AATInteractiveTurnpointIntegration.kt`
    - `AATManageListTypeInference.kt`
    - `AATMapInteractionHandler.kt`, `AATAreaTapDetector.kt`
    - `AATMovablePointManager.kt`, `AATMovablePointGeometrySupport.kt`, `AATMovablePointStrategySupport.kt`
    - `AATMapRenderer.kt`, `AATTargetPointPinRenderer.kt`
    - `AATMapVisualIndicators.kt`, `AATMapVisualStatusIndicators.kt`
    - `FAIComplianceRules.kt`, `FAIComplianceTaskRules.kt`, `FAIComplianceAreaRules.kt`
    - `AATWaypointManager.kt`, `AATWaypointInitializationSupport.kt`, `AATWaypointMutationSupport.kt`
    - `DefaultAATTaskEngine.kt`, `AATTaskWaypointCodec.kt`
    - `RacingBoundaryCrossingPlanner.kt`, `RacingBoundaryCrossingMath.kt`
    - `KeyholeGeometry.kt`, `KeyholeShapeSupport.kt`
- Test-net expansion (targeted):
  - added `RacingNavigationEngineSupportTest.kt` coverage for:
    - task signature drift detection
    - state normalization on signature change
    - transition-evaluation gate behavior (far-out and near-window cases)
- Test-net expansion (typed parameter contract hardening):
  - added `TaskWaypointCustomParamsTest.kt` coverage for:
    - AAT time custom-parameter apply/remove behavior
    - AAT waypoint fallback/customization parsing behavior
    - racing waypoint custom-parameter roundtrip behavior
    - target state fallback parsing behavior
    - persisted OZ parameter parsing + serialization behavior
- Boundary hardening (typed persisted OZ contract):
  - added `PersistedOzParams` in
    `feature/map/src/main/java/com/example/xcpro/tasks/core/TaskWaypointCustomParams.kt`
    and routed OZ default/build/parse through it.
  - removed raw `ozParams["..."]` parsing from
    `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt`.
  - updated `feature/map/src/main/java/com/example/xcpro/tasks/TaskPersistSerializer.kt`
    to emit OZ maps via typed contract instead of string-key map literals.
  - updated `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`
    to emit racing custom parameters via `RacingWaypointCustomParams` instead
    of raw string-key map literals.
- Guardrail add-on (contract regression prevention):
  - `scripts/ci/enforce_rules.ps1` now fails builds on raw
    `ozParams["..."]` indexing in production task code.
- Phase 1 decomposition pass completed (map overlay/runtime tranche 4):
  - `MapScreenContentOverlays.kt` reduced from 349 -> 106 LOC.
  - `FlightDataManager.kt` reduced from 343 -> 301 LOC.
  - `AATPathOptimizer.kt` reduced from 342 -> 171 LOC.
  - extracted focused collaborators:
    - `MapTrafficDebugPanels.kt`
    - `MapReplayDiagnosticsLogger.kt`
    - `FlightDataManagerSupport.kt`
- Phase 2 guardrail add-on completed (tranche 4):
  - `scripts/ci/enforce_rules.ps1` now also enforces:
    - raw task custom-parameter literal pair ban in production task code
      (`"targetLat" to ...`, `"targetParam" to ...`, etc.).
    - raw `customParameters["..."]` indexing ban in production task code.
    - tighter size budgets for:
      - `MapScreenContentOverlays.kt` (`<= 250`)
      - `MapTrafficDebugPanels.kt` (`<= 250`)
      - `MapReplayDiagnosticsLogger.kt` (`<= 120`)
      - `FlightDataManager.kt` (`<= 320`)
      - `AATPathOptimizer.kt` (`<= 250`)
- Instrumentation test-net expansion (app androidTest):
  - added `TaskPersistSerializerInstrumentedTest.kt` with map/task persistence
    coverage for:
    - AAT default OZ serialization contract
    - racing default OZ serialization contract
    - target fallback hydration on import
    - OZ payload preservation on import
    - waypoint order/role roundtrip integrity
    - re-encode stability after deserialize
- Phase 1 decomposition pass completed (MapScreenViewModel collaborator split):
  - `MapScreenViewModel.kt` observer wiring moved into focused binding helper:
    - `MapScreenViewModelObservers.kt`
  - VM state construction moved into focused builder helpers:
    - `MapScreenViewModelStateBuilders.kt`
  - extracted builder coverage now includes:
    - replay sensor gating (`suppressLiveGps`, `allowSensorStart`)
    - ADS-B metadata merge precedence behavior
    - card hydration readiness gate
    - flight-mode mapping safety
- Phase 1 decomposition pass completed (TaskSheetViewModel import/target split):
  - `TaskSheetViewModel.kt` target sync duplication removed via:
    - `syncAatTargetAt(index)`
  - persisted import workflow split into focused collaborators:
    - `importWaypoints(...)`
    - `applyImportedTargets(...)`
    - `applyImportedObservationZones(...)`
    - `applyAatObservationZone(...)`
    - `applyRacingObservationZone(...)`
  - goal: reduce mutation-path blast radius while preserving import behavior.
- Phase 1 decomposition pass completed (MapScreenRoot orchestration split):
  - `MapScreenRoot.kt` reduced from `327 -> 231` LOC by moving binding/layout/effects
    orchestration into focused collaborators:
    - `MapScreenRootStateBindings.kt`
    - `MapScreenRootHelpers.kt` (`rememberMapScreenWidgetLayoutBinding`)
    - `MapScreenRootEffects.kt` (`MapScreenComposeAndLifecycleEffects`)
- Boundary hardening completed (MapScreenViewModel runtime surface narrowing):
  - added grouped runtime contract:
    - `MapScreenRuntimeDependencies.kt`
  - `MapScreenViewModel.kt` now exposes runtime collaborators via
    `runtimeDependencies` instead of multiple direct manager/use-case fields.
  - direct UI-facing fields removed from VM public surface:
    - `mapSensorsRuntimeUseCase`
    - `mapTasksRuntimeUseCase`
    - `mapFeatureFlags`
    - direct public `flightDataManager` and `orientationManager`
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt` migrated to
    `mapViewModel.runtimeDependencies.flightDataManager`.
- Test-net expansion (MapScreenViewModel collaborator guards):
  - added `MapScreenViewModelStateBuildersTest.kt` coverage for:
    - metadata enrichment merge-by-id behavior and fallback
    - replay selection/status gating behavior
    - card hydration dual-readiness gating
- Phase 2 guardrail add-on completed (tranche 5):
  - `scripts/ci/enforce_rules.ps1` now also enforces size budgets for:
    - `MapScreenRoot.kt` (`<= 250`)
    - `MapScreenRootStateBindings.kt` (`<= 120`)
    - `MapScreenRootHelpers.kt` (`<= 250`)
    - `MapScreenRootEffects.kt` (`<= 220`)
    - `MapScreenScaffoldInputs.kt` (`<= 320`)
    - `TaskSheetViewModel.kt` (`<= 320`)
    - `FlightMode` -> card mode mapping contract
- Test-net expansion (TaskSheet import behavior guards):
  - added `TaskSheetViewModelImportTest.kt` coverage for:
    - AAT import path applies target sync + OZ shape mapping + active-leg reset
    - Racing import path applies gate-width mapping only for turnpoints
      (with no AAT mutation calls)
- Test-net expansion (render sync + replay determinism hardening):
  - expanded `TaskRenderSyncCoordinatorTest.kt` coverage for:
    - distinct task signature transitions (`taskHash`/`taskType`/`activeLeg`)
      each trigger sync
    - style reload on ready map forces resync
    - clear + mutation while map unavailable preserves `clear -> sync` ordering
      when map becomes ready
  - expanded `RacingReplayValidationTest.kt` with repeated-run state-trace parity
    assertions (status/leg/event sequence deterministic across runs)
- Guardrail hardening (task/map composable boundary):
  - `scripts/ci/enforce_rules.ps1` now fails on
    `rememberTaskManagerCoordinator(...)` usage outside
    `TaskManagerCompat.kt` in task/map-task UI surfaces.
  - ignored/disabled test scan scope widened to all
    `feature/map/src/test/java/**/*.kt` files.
- Verification rerun (sequential, post-clean):
  - `./gradlew enforceRules` -> PASS
  - `./gradlew testDebugUnitTest` -> PASS
  - `./gradlew assembleDebug` -> PASS
  - `./gradlew enforceRules testDebugUnitTest assembleDebug` -> PASS
  - `./gradlew :feature:map:compileDebugKotlin :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapScreenViewModelStateBuildersTest" --tests "com.example.xcpro.tasks.core.TaskWaypointCustomParamsTest"` -> PASS
  - `./gradlew :feature:map:compileDebugKotlin` -> PASS (after tranche 2 split)
  - `./gradlew :feature:map:compileDebugKotlin` -> PASS (after tranche 3 split)
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.core.TaskWaypointCustomParamsTest"` -> PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapScreenViewModelStateBuildersTest"` -> PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.TaskSheetViewModelImportTest" --tests "com.example.xcpro.map.MapScreenViewModelStateBuildersTest"` -> PASS
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` -> PASS

Post-pass hotspot snapshot (remaining > 350 LOC):

- Task/map-task hotspot set from this plan tranche: none.

## 1) Scope

- Problem statement:
  - Map/task slice still has high blast radius from large orchestration files,
    wide responsibilities, and limited instrumentation depth.
- Why now:
  - Current architecture is stable enough to complete decomposition safely, and
    this is the highest ROI path to durable feature velocity.
- In scope:
  - Map/task orchestration decomposition.
  - Boundary hardening for render ownership and UI isolation.
  - Deterministic tests for risky map/task vertical slices.
  - CI guardrails for structural drift.
- Out of scope:
  - Full app-wide refactor outside map/task packages.
  - UI redesign work.
  - New product features unrelated to map/task stability.
- User-visible impact:
  - Lower regression rate, faster safe iteration, fewer delayed bug fixes.

## 1A) Baseline Evidence (2026-02-14)

Current largest map files:

- `feature/map/src/main/java/com/example/xcpro/map/MapScreenReplayCoordinator.kt`: 514
- `feature/map/src/main/java/com/example/xcpro/map/MapCameraManager.kt`: 403
- `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`: 399
- `feature/map/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt`: 356
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`: 347

Current largest task files:

- `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngine.kt`: 510
- `feature/map/src/main/java/com/example/xcpro/tasks/RulesBTTab.kt`: 469
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskDisplay.kt`: 469
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/calculations/AATDistanceCalculator.kt`: 460
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskCalculator.kt`: 454
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskValidator.kt`: 441
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt`: 418
- `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`: 407

Test depth snapshot:

- map/task unit test annotations: 222
- adsb unit test annotations: 75
- app instrumentation test annotations: 2

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Task definitions + active leg | task repository/coordinator | use-case snapshots/flows | composable-local manager mirrors |
| Task render snapshot | `MapTasksUseCase` | `TaskRenderSnapshot` | ad-hoc map/runtime copies |
| Task render apply/clear side effect | `TaskRenderSyncCoordinator` | coordinator-owned render/clear dispatch | direct router calls from multiple owners |
| Map/task UI state | `MapScreenViewModel` and task VMs | immutable `StateFlow` | manager internals read from UI |
| Replay task timeline state | replay/domain coordinators | replay state flows/events | wall-time based shortcuts in UI |

### 2.2 Dependency Direction

Dependency flow must remain:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/map/**`
  - `feature/map/src/main/java/com/example/xcpro/tasks/**`
  - `scripts/ci/enforce_rules.ps1`
  - targeted tests in `feature/map/src/test/**` and `app/src/androidTest/**`
- Boundary risk:
  - Refactor may accidentally move domain policy into UI helpers.
  - Refactor may expose manager handles for convenience.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Replay orchestration branching in single class | `MapScreenReplayCoordinator` | focused replay collaborators (router/state/applier split) | reduce blast radius | replay parity tests |
| Camera policy + SDK plumbing mixed | `MapCameraManager` | camera policy evaluator + SDK adapter split | isolate pure policy testing | unit tests for policy |
| Large task geometry routing blocks | monolithic managers/engines | pure geometry/domain calculators + thin orchestrators | improve safe edits | deterministic geometry tests |
| UI/task mutation safety | mixed callsites | VM/use-case intent-only callsites | enforce UDF | enforceRules + UI tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Any direct non-owner render sync call | direct `TaskMapRenderRouter` call risk | coordinator-owned `TaskRenderSyncCoordinator` only | 2 |
| Any manager mutation from composable | direct manager method call risk | VM intent -> use-case -> coordinator | 2 |
| Any raw manager escape-hatch exposure | use-case/viewmodel leakage risk | narrow facade contract per domain op | 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| live map/task timing windows | Monotonic | stable deltas and freshness |
| replay timing and sequencing | Replay | deterministic same-input behavior |
| UI labels/timestamps only | Wall | user-facing display only |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Main: map SDK and UI rendering operations.
  - Default: geometry/policy calculations.
  - IO: persistence and file I/O.
- Primary cadence/gating sensor:
  - task render sync is event-driven by task/map/style mutations.
- Hot-path latency budget:
  - task mutation -> render dispatch <= 100 ms test budget.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (required)
- Randomness used: No
- Replay/live divergence rules:
  - replay path uses replay timestamps only.
  - live path uses monotonic timing for validity/deltas.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| direct render bypass reintroduced | ARCHITECTURE 5B + CODING_RULES 1A | enforceRules + unit test | `scripts/ci/enforce_rules.ps1`, `TaskRenderSyncCoordinatorTest` |
| UI mutates task managers directly | ARCHITECTURE 1 + 5B | enforceRules structural scan | `scripts/ci/enforce_rules.ps1` |
| manager/domain Compose state leakage | ARCHITECTURE 5B + CODING_RULES 4 | enforceRules forbidden pattern scan | `scripts/ci/enforce_rules.ps1` |
| hotspot growth/mega files return | CODING_RULES 1, 15A | CI size-budget check + review gate | `scripts/ci/enforce_rules.ps1` |
| replay behavior drift | ARCHITECTURE 4A + 5B | deterministic replay tests | `RacingReplayValidationTest` + new parity tests |
| map/task vertical-slice regressions | CONTRIBUTING 3A | integration + connected tests | new map/task slice tests + `:app:connectedDebugAndroidTest` |

## 3) Data Flow (Before -> After)

Before (high risk):

```
Task mutation/style/map event -> multiple orchestration surfaces -> render side effects
```

After (target):

```
Task UI intent -> ViewModel -> MapTasksUseCase -> task owner state
-> TaskRenderSnapshot -> TaskRenderSyncCoordinator (single owner)
-> TaskMapRenderRouter -> map SDK
```

## 4) Implementation Phases

### Phase 0: Baseline Lock

- Goal:
  - Freeze baseline metrics and verify current rule gates.
- Files to change:
  - plan docs and metrics snapshot doc only.
- Tests to add/update:
  - none.
- Exit criteria:
  - baseline scorecard and hotspot list committed in docs.

### Phase 1: Map Orchestration Decomposition

- Goal:
  - Split high-risk map orchestrators into smaller units.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenReplayCoordinator.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapCameraManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`
  - new focused collaborators under `feature/map/src/main/java/com/example/xcpro/map/`
- Tests to add/update:
  - replay coordinator routing/unit tests
  - camera policy unit tests
- Exit criteria:
  - no target file > 350 LOC in this phase scope.
  - behavior parity verified by unit tests.

### Phase 2: Task Runtime Boundary Lock

- Goal:
  - Ensure one render owner path and remove residual UI/manager bypasses.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapRenderRouter.kt`
  - task UI callsites under:
    - `feature/map/src/main/java/com/example/xcpro/tasks/**`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/task/**`
  - `scripts/ci/enforce_rules.ps1`
- Tests to add/update:
  - `TaskRenderSyncCoordinatorTest`
  - vertical-slice integration tests for
    `toggle/edit/clear -> state -> sync -> render`
- Exit criteria:
  - zero direct non-owner render calls.
  - zero composable direct manager mutation/query in guarded scopes.

### Phase 3: Task Domain Hotspot Decomposition

- Goal:
  - Reduce change risk in largest task files while preserving behavior.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngine.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskCalculator.kt`
  - `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATTaskValidator.kt`
  - optional follow-up: `RulesBTTab.kt`, `AATTaskDisplay.kt`
- Tests to add/update:
  - deterministic geometry and boundary-policy tests
  - regression tests for racing/AAT transition logic
  - AAT start/finish cylinder radius contract tests (configured radius must
    match render/geometry/validation radius; no runtime halving)
- Exit criteria:
  - each refactored file <= 350 LOC or exception documented with owner+expiry.
  - geometry/policy functions covered by pure JVM tests.
  - AAT cylinder radius contract is consistent end-to-end.

### Phase 4: Test Net and Instrumentation Depth

- Goal:
  - Raise confidence on risky paths to release-grade depth.
- Files to change:
  - `feature/map/src/test/java/com/example/xcpro/map/**`
  - `feature/map/src/test/java/com/example/xcpro/tasks/**`
  - `app/src/androidTest/java/com/example/xcpro/**`
- Tests to add/update:
  - map/task lifecycle and style-reload regression tests
  - replay parity tests (same input twice -> identical outputs)
  - additional instrumentation around overlay/task transitions
- Exit criteria:
  - app instrumentation test count in this slice > 2 (material increase).
  - two consecutive stable connected test passes.

### Phase 5: Guardrails + Rescore + Signoff

- Goal:
  - Lock improvements and prevent architecture drift.
- Files to change:
  - `scripts/ci/enforce_rules.ps1`
  - relevant architecture docs if pipeline/ownership wording changed.
- Tests to add/update:
  - enforceRules guard tests or script validation tests as available.
- Exit criteria:
  - all required commands pass.
  - maintainability/change safety rescored to `5.0 / 5`.

## 5) Test Plan

- Unit tests:
  - decomposition behavior parity, geometry policy, render sync contracts.
  - AAT radius contract parity across renderer/geometry/validation paths.
- Replay/regression tests:
  - deterministic transition parity for racing/AAT map/task routes.
- UI/instrumentation tests:
  - map lifecycle + task overlay transition flows.
- Degraded/failure-mode tests:
  - map unavailable, style reload mid-edit, clear while map null.
- Boundary tests for removed bypasses:
  - enforceRules and focused tests for forbidden call patterns.

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

Local fast device-preserving loop:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Large-file split introduces subtle behavior drift | High | add parity tests before and after split | XCPro Team |
| Over-refactor with low value | Medium | prioritize hotspot + bypass + test phases only | XCPro Team |
| Connected tests are slow/flaky | Medium | run targeted app instrumentation in loop, full connected at phase gates | XCPro Team |
| New bypasses reappear during feature work | High | enforceRules structural scans + PR checklist | XCPro Team |
| Branch churn conflicts | Medium | phase by phase, small PRs, no unrelated edits | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling explicit in code and tests.
- Replay behavior deterministic on repeated inputs.
- AAT start/finish cylinder radius semantics are consistent end-to-end (no
  runtime `/2` on persisted/configured radius values).
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved.

Quantitative gates for maintainability `5.0 / 5`:

- map/task orchestration hotspot files in active scope <= 350 LOC, or
  time-boxed documented exception.
- one owner path for task render apply/clear.
- zero composable direct manager mutation/query in guarded task/map UI scopes.
- expanded instrumentation coverage for risky map/task transitions.
- two consecutive connected test passes on reference device/emulator.

## 8) Rollback Plan

- What can be reverted independently:
  - each phase/PR is isolated by collaborator split or guard/test addition.
- Recovery steps if regression is detected:
  1. Revert offending phase commit(s) only.
  2. Keep guard/test additions that still pass.
  3. Re-run required checks and reopen the phase with narrower scope.
