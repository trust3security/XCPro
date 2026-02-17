
# KNOWN_DEVIATIONS.md

Audit date: 2026-02-14

This file lists known deviations from ARCHITECTURE.md and CODING_RULES.md.
Each entry must include an issue ID, owner, and expiry date.

Active remediation plan:
- `docs/refactor/Map_Task_Maintainability_5of5_Refactor_Plan_2026-02-14.md`

## Current deviations

None.

## Verification

Last verified: 2026-02-14
- Commands:
  - ./gradlew enforceRules
  - ./gradlew testDebugUnitTest
  - ./gradlew assembleDebug
  - ./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true" (run twice)

## Resolved deviations

1) MapScreenViewModel uses platform APIs/managers instead of use-cases
- Rule: ViewModels depend on use-cases only; no platform APIs or I/O in ViewModels.
- Issue: RULES-20260204-01
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: MapScreenViewModel now depends on use-case wrappers; Context/WaypointLoader moved to MapWaypointsUseCase; Log removed.

2) TaskSheetViewModel holds UI map handle and constructs dependencies
- Rule: ViewModels must not reference UI types; dependencies must be injected; use-cases only.
- Issue: RULES-20260204-02
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: MapLibreMap removed from VM; TaskSheetUseCase and TaskSheetCoordinatorUseCase injected.

3) IgcReplayViewModel depends on controller and platform logging
- Rule: ViewModels depend on use-cases only; no platform APIs in ViewModels.
- Issue: RULES-20260204-03
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: IgcReplayUseCase added and injected; Log removed from VM.

4) FlightDataViewModel constructs Clock/use-case internally
- Rule: Dependencies must be injected; ViewModels should not construct use-cases or clocks.
- Issue: RULES-20260204-04
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: FlightCardsUseCaseFactory injected; VM no longer constructs clock/use-case.

5) TaskFilesUseCase uses wall time directly
- Rule: Domain/use-case logic must use injected Clock; no Date/System time direct calls.
- Issue: RULES-20260204-05
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: TaskFilesUseCase now uses injected Clock for timestamp formatting.

6) UI imports sensor/data models directly
- Rule: UI code never imports data repositories or sensor/data models directly.
- Issue: RULES-20260204-06
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: Map UI now uses UI-safe models mapped in ViewModel.

7) Un-gated println logging in production paths
- Rule: No logs in tight loops; logs must not be required for correctness; avoid logging location data in release builds.
- Issue: RULES-20260204-07
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: println removed from production sources.

8) Global mutable feature flags via Kotlin object singletons
- Rule: No hidden singletons holding mutable state.
- Issue: RULES-20260204-08
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: MapFeatureFlags and TaskFeatureFlags are injected @Singleton classes.

9) Default wall-time usage in replay parser without injected clock
- Rule: Domain logic should use injected time sources; avoid implicit wall time.
- Issue: RULES-20260204-09
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: IgcParser injected with Clock; default date derived from injected time.

10) AAT map editing uses SystemClock directly
- Rule: Domain/use-case logic must use injected Clock; no SystemClock/System.currentTimeMillis direct calls.
- Issue: RULES-20260204-10
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: AAT edit state, tap timestamps, and overlays now use injected Clock.

11) Timebase usage in domain/fusion
- Rule: injected clock only; no System.currentTimeMillis in domain or fusion.
- Resolved: 2026-01-27
- Notes: Removed direct system time usage; injected Clock or explicit timestamps.

12) DI: pipeline constructed inside manager
- Rule: core pipeline components must be injected.
- Resolved: 2026-01-27
- Notes: Sensor fusion pipeline provided via Hilt; managers no longer construct it directly.

13) ViewModel purity
- Rule: ViewModels must not touch SharedPreferences or UI types.
- Resolved: 2026-01-27
- Notes: SharedPreferences moved to repositories/use-cases; Compose types removed from ViewModels; tests updated.

14) Compose lifecycle collection
- Rule: use collectAsStateWithLifecycle for UI state.
- Resolved: 2026-01-27
- Notes: Replaced collectAsState with collectAsStateWithLifecycle in UI layers.

15) Vendor string policy
- Rule: no "xcsoar"/"XCSoar" literals in production Kotlin source.
- Resolved: 2026-01-27
- Notes: Vendor literals removed/renamed in production Kotlin.

16) ASCII hygiene
- Rule: no non-ASCII characters in production Kotlin source.
- Resolved: 2026-01-27
- Notes: Non-ASCII characters replaced with ASCII equivalents in production Kotlin.

17) Task coordinator constructed persistence/managers directly
- Rule: DI; core collaborators injected, not constructed inside managers/coordinators.
- Issue: RULES-20260211-04
- Owner: XCPro Team
- Resolved: 2026-02-11
- Notes:
  - `TaskManagerCoordinator` no longer constructs managers/prefs or owns `Context`; collaborators are injected.
  - `TaskManagerCompat.rememberTaskManagerCoordinator` now resolves via Hilt ViewModel host (`TaskManagerCoordinatorHostViewModel`) with no runtime entry-point lookup.
  - Coordinator map-instance ownership and map-typed AAT edit APIs were removed; UI runtime redraw routes through `TaskMapRenderRouter`.

18) TaskSheetViewModel contained business geospatial math/policy
- Rule: ViewModel rules; no business math in ViewModels.
- Issue: RULES-20260211-01
- Owner: XCPro Team
- Resolved: 2026-02-11
- Notes:
  - Proximity policy moved to `TaskProximityEvaluator` in domain logic.
  - `TaskSheetViewModel` now delegates proximity and distance checks through `TaskSheetUseCase`/`TaskRepository`.

19) Task Composables mutated task state directly through TaskManagerCoordinator
- Rule: MVVM + UDF; UI emits intents to ViewModel only.
- Issue: RULES-20260211-02
- Owner: XCPro Team
- Resolved: 2026-02-11
- Notes:
  - `TaskSearchBarsOverlay` now emits add-waypoint intents via callback instead of mutating manager directly.
  - `TaskTopDropdownPanel` and `SwipeableTaskBottomSheet` task-type switching now routes through `TaskSheetViewModel` with `uiState`-backed selection.

20) Task Composables read manager internals directly as UI state
- Rule: SSOT/UDF; UI renders ViewModel state, not manager internals.
- Issue: RULES-20260211-03
- Owner: XCPro Team
- Resolved: 2026-02-11
- Notes:
  - `MapTaskScreenUi` minimized indicator now renders from `TaskSheetViewModel.uiState` and VM intents.
  - `AATManageList`, `CommonTaskComponents`, and `BottomSheetState` no longer read manager internals for UI state.

21) FlightDataViewModel constructed helper directly
- Rule: Dependencies should be injected (or created via injected factory) in ViewModels.
- Issue: RULES-20260211-07
- Owner: XCPro Team
- Resolved: 2026-02-11
- Notes:
  - Added `FlightDataTemplateManagerFactory` and injected it into `FlightDataViewModel`.
  - `FlightDataTemplateManager` is now created via the injected factory.

22) Use-case wrappers leaked raw manager/controller handles
- Rule: Use-cases expose domain operations and flows, not raw manager/controller escape hatches.
- Issue: RULES-20260211-06
- Owner: XCPro Team
- Resolved: 2026-02-11
- Notes:
  - `MapScreenUseCases` no longer exposes `taskManager`, `taskNavigationController`, `serviceManager`, or replay controller handles.
  - `MapScreenViewModel` no longer publishes raw task/sensor/replay manager handles.
  - Replay adapter/coordinator wiring now routes through use-case factory APIs.

23) Non-UI task managers used Compose runtime state primitives
- Rule: Non-UI/domain/manager classes must not depend on Compose runtime state.
- Issue: RULES-20260211-05
- Owner: XCPro Team
- Resolved: 2026-02-11
- Notes:
  - Replaced manager-held `mutableStateOf` with `MutableStateFlow` in `RacingTaskManager`, `AATTaskManager`, and `AATNavigationManager`.
  - Removed remaining non-composable manager Compose state usage from `AATEditModeManager`.

24) Task coordinator exposed manager escape-hatch APIs
- Rule: Coordinator/use-case boundaries must not expose raw manager handles.
- Issue: RULES-20260211-08
- Owner: XCPro Team
- Resolved: 2026-02-11
- Notes:
  - Removed `getRacingTaskManager()` and `getAATTaskManager()` from `TaskManagerCoordinator`.
  - Task map rendering now consumes `TaskRenderSnapshot` from `MapTasksUseCase` through `TaskRenderSyncCoordinator` and shared Racing/AAT task mappers.
  - `enforceRules` now blocks reintroduction of coordinator manager escape-hatch APIs.

25) Task managers exposed MapLibre render/edit APIs
- Rule: Map rendering/editing must remain UI/runtime-only; task managers stay map-agnostic.
- Issue: RULES-20260211-09
- Owner: XCPro Team
- Resolved: 2026-02-11
- Notes:
  - Removed MapLibre imports and manager-level map render/edit methods from `RacingTaskManager` and `AATTaskManager`.
  - Manager leg setters are now map-agnostic (`setRacingLeg(index)` / `setAATLeg(index)`).
  - Task map rendering now routes via UI/runtime `TaskMapRenderRouter` and coordinator core-task snapshots.
  - `enforceRules` now blocks MapLibre imports/legacy map APIs in task managers and Android/UI imports under `tasks/domain`.

