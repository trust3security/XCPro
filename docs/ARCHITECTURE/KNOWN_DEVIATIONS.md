
# KNOWN_DEVIATIONS.md

Audit date: 2026-02-18

This file lists known deviations from ARCHITECTURE.md and CODING_RULES.md.
Each entry must include an issue ID, owner, and expiry date.

Active remediation plan:
- `docs/refactor/Map_Task_Maintainability_5of5_Refactor_Plan_2026-02-14.md`

README consistency rule:
- `docs/ARCHITECTURE/README.md` must never duplicate or summarize deviation status.
- This file is the only authoritative deviation ledger.

## Entry template

Use this template for every new deviation entry:

1) `<short title>`
- Rule:
- Issue:
- Owner:
- Expiry:
- Scope:
- Risk:
- Mitigation:
- Removal steps:
- Exit criteria:

## Current deviations

1) Legally required weather provider literals and attribution link usage in implementation internals
- Rule: Vendor neutrality (`ARCHITECTURE.md`: no vendor names in production strings or public APIs).
- Issue: RULES-20260220-11
- Owner: XCPro Team
- Expiry: 2026-06-30
- Scope:
  - `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherRainAttribution.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherRainTileUrlBuilder.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherRadarMetadataRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
- Rationale:
  - Weather provider endpoints/host validation and legally required source attribution require provider literals.
  - UI/public API naming remains provider-neutral outside explicit attribution/compliance surfaces.
  - Deviation is bounded to weather integration internals and required attribution link handling.

Compliance note (2026-02-20):
- This deviation is time-boxed and must be removed by either:
  - architecture wording update that explicitly allows legally required attribution literals, or
  - provider abstraction changes that remove direct literals from production code.
- Deep-pass findings and closure steps are tracked in
  `docs/RAINVIEWER/01_RAINVIEWER_INDUSTRY_HARDENING_PLAN_2026-02-20.md`.

2) MAPSCREEN `pkg-e1` fails `MS-UX-01` threshold gate in strict completion contract runs
- Rule: Map visual SLO gate (`CODING_RULES.md` section `1A Enforcement`; `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`).
- Issue: RULES-20260305-12
- Owner: XCPro Team
- Expiry: 2026-04-15
- Scope:
  - `artifacts/mapscreen/phase3/pkg-e1/20260305-193049/`
  - `artifacts/mapscreen/phase3/pkg-e1/20260305-195205/`
  - `scripts/qa/run_mapscreen_completion_contract.ps1` (phase-5 gate evidence)
- Risk:
  - Map interaction smoothness does not meet required `MS-UX-01` p95/p99/jank thresholds under automated gesture capture.
- Mitigation:
  - Keep phase-2 package lanes (`pkg-d1`, `pkg-g1`, `pkg-w1`) green and isolated.
  - Continue targeted `pkg-e1` runtime work with strict Tier A/B evidence capture before release promotion.
  - Do not mark `pkg-r1` green unless this deviation is removed or an approved release exception is explicitly documented.
  - Defer final `MS-UX-01` closure to next focused optimization cycle (tracked in `docs/MAPSCREEN` execution backlog/contract docs).
- Removal steps:
  - Deliver additional `MS-UX-01` runtime optimizations and re-run strict contract without allow-failure flags.
  - Produce a `pkg-e1` artifact where phase-5 verification reports `ready_for_promotion`.
  - Remove this entry after successful strict completion contract run (phases `0..8`) for the same code line.
- Exit criteria:
  - `verify_mapscreen_package_evidence.ps1 -PackageId pkg-e1` passes with no failed SLOs.
  - `run_mapscreen_completion_contract.ps1` reaches phase 8 with no allow-failure flags.

## Verification

Last verified: 2026-03-05
- Commands:
  - python scripts/arch_gate.py
  - ./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.tasks.*" --tests "com.example.xcpro.tasks.domain.*" --tests "com.example.xcpro.tasks.aat.*"
  - ./gradlew enforceRules
  - ./gradlew testDebugUnitTest
  - ./gradlew assembleDebug

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

