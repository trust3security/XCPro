# Feature:Map Compile Speed Release-Grade Phased Plan

## 0) Metadata

- Title: Reduce `feature:map` incremental compile cost without behavior drift
- Owner: Codex
- Date: 2026-03-12
- Issue/PR: TBD
- Status: In Progress
- Progress note:
  - 2026-03-12: Phase 1 implemented for `feature:map` Room cleanup; removed unused Room runtime and Room KSP dependencies after repo-wide usage verification.
  - 2026-03-12: Phase 2 repass found forecast ownership is already split between `feature:profile` and `feature:map`; Phase 2 is now split into a no-churn `feature:forecast` bootstrap (Phase 2A) and a later map-owned forecast slice move (Phase 2B).
  - 2026-03-12: Phase 2A implemented; created `feature:forecast`, moved the shared forecast foundations and two shared-owner tests into it, and rewired `feature:map` plus `feature:profile` to depend on the new module without app/nav churn.
  - 2026-03-12: Verification for Phase 2A: `:feature:forecast:testDebugUnitTest`, `testDebugUnitTest`, and `assembleDebug` passed; `enforceRules` still fails only on the pre-existing `MapScreenViewModel.kt` line-budget gate.
  - 2026-03-12: Phase 2B repass found the whole `feature:map/forecast` package is map-agnostic and should move as one unit, but `ForecastSettingsScreen` is still an app + local-subsheet compatibility entrypoint and should stay in `feature:map` initially to avoid app dependency churn.
  - 2026-03-12: Phase 2B also requires `feature:forecast` bootstrap work before the move: direct ownership of `SKYSIGHT_API_KEY` `BuildConfig`, forecast DI modules/qualifiers, and direct dependencies on the current forecast runtime stack (`core:common`, `core:time`, security crypto, OkHttp-based request path, and viewmodel/runtime support).
  - 2026-03-12: Phase 2B implemented; moved the map-agnostic forecast runtime/auth/provider package, forecast DI, forecast settings view-model/use-case, and the forecast-owned test suite into `feature:forecast` while keeping `ForecastSettingsScreen` plus the map runtime/UI shell in `feature:map`.
  - 2026-03-12: Verification for Phase 2B: `:feature:forecast:testDebugUnitTest`, `testDebugUnitTest`, and `assembleDebug` passed; `enforceRules` still fails only on the pre-existing `MapScreenViewModel.kt` line-budget gate.
  - 2026-03-12: Phase 7A implemented as a low-churn internal contract extraction inside `feature:map`:
    - extracted top-level map-state contract types (`MapPoint`, `MapSize`, `CameraSnapshot`) and switched runtime-facing state interfaces away from nested `MapStateStore` DTOs
    - isolated `MapSensorsUseCase`, `MapTasksUseCase`, and `TaskRenderSnapshot` into dedicated runtime-facing files
    - removed `trailSettings` from `MapStateReader` and rewired shell/runtime trail observation through `MapScreenViewModel.trailSettings`
    - kept `MapStateStore`, `MapStateActionsDelegate`, `MapScreenState`, and `MapScreenRuntimeDependencies` in `feature:map` for later `7B`
  - 2026-03-12: Verification for Phase 7A:
    - `./gradlew :feature:map:compileDebugKotlin` passed
    - broader repo gates were deferred until after doc sync; existing unrelated repo blockers remain outside 7A scope
  - 2026-03-12: Phase 7B bootstrap implemented as the lowest-churn runtime-module split:
    - created `:feature:map-runtime` and rewired `feature:map` to depend on it
    - moved the already-runtime-facing contracts/models into `:feature:map-runtime`:
      - `MapStateReader`
      - `MapStateActions`
      - `MapPoint`
      - `MapSize`
      - `CameraSnapshot`
      - `DisplayPoseMode`
      - `DisplayPoseSmoothingConfig`
      - `DisplaySmoothingProfile`
      - `MapFeatureFlags`
      - `MapLocationUiModel` / `GpsStatusUiModel`
    - moved the clean runtime/task bridge files into `:feature:map-runtime`:
      - `MapTasksUseCase`
      - `TaskRenderSyncCoordinator`
    - kept `MapSensorsUseCase` in `feature:map` because it still depends on `VarioServiceManager`, which remains shell-owned in `feature:map`
    - kept `rememberMapScreenManagers(...)` in `feature:map` and made the shell pass `TaskMapRenderRouter` into `TaskRenderSyncCoordinator` explicitly, so the runtime module no longer depends on shell-owned render routing
  - 2026-03-12: Verification for Phase 7B bootstrap:
    - `./gradlew :feature:map-runtime:compileDebugKotlin` passed
    - `./gradlew :feature:map:compileDebugKotlin` passed
    - full repo verification rerun pending after doc sync
  - 2026-03-12: Overlay/runtime core extraction now has a dedicated execution plan:
    - `docs/refactor/Map_Overlay_Runtime_Core_Extraction_Plan_2026-03-12.md`
    - exact dependency pass confirmed the first real runtime move should be the forecast/weather overlay runtime leaf cluster, not a standalone builder/factory phase
  - 2026-03-12: Overlay/runtime core Phase B implemented as the first real runtime move:
    - moved the forecast/weather overlay runtime leaf cluster into `:feature:map-runtime`
    - added `ForecastWeatherOverlayRuntimeState` plus the shell-side `MapForecastWeatherOverlayRuntimeStateAdapter`
    - extracted a shared runtime layer-id constant so the moved renderers no longer depend on `BlueLocationOverlay`
    - kept `MapOverlayManager.kt` and `MapOverlayManagerRuntime.kt` in `feature:map` as the shell-facing runtime owner/adapters
  - 2026-03-12: Verification for overlay/runtime core Phase B:
    - `./gradlew :feature:map-runtime:compileDebugKotlin` passed
    - `./gradlew :feature:map:compileDebugKotlin` passed
    - broader repo gates continue to be blocked only by the existing unrelated failures in `ProfileRepositoryTest.kt` and the `MapScreenViewModel.kt` line-budget gate
  - 2026-03-12: Overlay/runtime core Phase C1 implemented:
    - added runtime-side shell-port contracts for overlay lifecycle/status
    - moved `MapOverlayRuntimeCounters` into `:feature:map-runtime`
    - removed direct `MapScreenState` ownership from `MapOverlayManagerRuntime.kt`
    - shifted shell delegate construction into `MapOverlayManager.kt` without changing the shell-facing API
  - 2026-03-12: Verification for overlay/runtime core Phase C1:
    - `./gradlew :feature:map-runtime:compileDebugKotlin` passed
    - `./gradlew :feature:map:compileDebugKotlin` passed
  - 2026-03-12: Exact overlay/runtime core Phase C2 seam pass completed:
    - `MapOverlayManagerRuntime.kt` is now the primary owner-move payload
    - `MapOverlayRuntimeInteractionDelegate.kt` must move with the runtime owner because `MapOverlayManagerRuntime.kt` constructs it directly; leaving it in `feature:map` would create a `:feature:map-runtime -> :feature:map` back-edge
    - `MapOverlayRuntimeInteractionDelegateTest.kt` must move with that helper because the delegate is `internal`
    - `MapOverlayManagerRuntime.kt` still carries stale shell imports that must be removed in the move slice because `:feature:map-runtime` does not depend on those owners
    - the existing `MapOverlayManager*` behavior tests stay shell-owned because they instantiate `MapOverlayManager`, not the runtime owner directly
  - 2026-03-12: Post-Phase 2B compile measurements:
    - one-line `feature:map` leaf edit -> `./gradlew :feature:map:compileDebugKotlin` about `64.4s`
    - one-line `feature:forecast` leaf edit -> `./gradlew :feature:forecast:compileDebugKotlin` about `21s`
    - interpretation: map-owned edits are still expensive, but forecast-only edits no longer require `feature:map` compilation and now stay inside the extracted leaf module
  - 2026-03-12: Phase 3A implemented; created `feature:weather`, moved the rain foundations, rain metadata/runtime/view-model slice, weather settings use-case/view-model, weather DI, and the weather-owned unit tests into the new module while keeping the drawer route and map overlay/render shells in `feature:map`.
  - 2026-03-12: Verification for Phase 3A: `:feature:weather:testDebugUnitTest`, `testDebugUnitTest`, and `assembleDebug` passed; `enforceRules` still fails only on the pre-existing `MapScreenViewModel.kt` line-budget gate.
  - 2026-03-12: Post-Phase 3A compile measurements:
    - one-line `feature:weather` leaf edit -> `./gradlew :feature:weather:compileDebugKotlin` about `18.9s`
    - one-line `feature:map` weather-shell leaf edit -> `./gradlew :feature:map:compileDebugKotlin` about `71.4s`
    - interpretation: weather-owned edits are now isolated to `feature:weather`, but the remaining `feature:map` shell is still too large and continues to dominate map-owned edit latency
  - 2026-03-12: Phase 5 repass found the no-churn task seam is smaller than `feature:map/tasks/*` as a whole:
    - `tasks` totals about `220` production files / `30,853` lines
    - about `156` production files / `20,114` lines are task-owned core/runtime/state code without direct Compose, MapLibre, or `com.example.xcpro.map.*` imports
    - about `64` production files / `10,739` lines are map-shell/render/UI glue and should remain in `feature:map` for the first extraction
  - 2026-03-12: Phase 5 repass also found task ownership already crosses module boundaries:
    - `app` currently provides the singleton task graph (`RacingTaskManager`, `AATTaskManager`, `TaskManagerCoordinator`)
    - the legacy `"task"` route and task-file bottom-sheet shells are still map/airspace/waypoint wrappers and should remain in `feature:map`
    - map task renderers and edit overlays still depend on `feature:map` `BuildConfig`, so they are not safe Phase 5A move candidates
  - 2026-03-12: Phase 5A implemented; created `feature:tasks`, moved the task-owned core/runtime/state slice and task-owned tests into it, rewired `app` and `feature:map` to depend on the new module, and kept the route wrappers plus MapLibre task shell in `feature:map`.
  - 2026-03-12: Verification for Phase 5A: `testDebugUnitTest` and `assembleDebug` passed; `enforceRules` now fails only on the pre-existing `MapScreenViewModel.kt` line-budget gate after rule ownership was updated to follow the extracted task files.
  - 2026-03-12: Post-Phase 5A compile measurements:
    - one-line `feature:tasks` leaf edit -> `./gradlew :feature:tasks:compileDebugKotlin` about `29.8s`
    - one-line retained `feature:map` task-shell leaf edit -> `./gradlew :feature:map:compileDebugKotlin` about `64.1s`
    - interpretation: task-core edits are now isolated to `feature:tasks`, while retained map task-shell edits still pay the heavy `feature:map` compile path
  - 2026-03-12: Phase 5B implemented; moved the task editor UI atoms into `feature:tasks` while keeping bottom-sheet wrappers, route compatibility, and MapLibre render/edit shells in `feature:map`.
  - 2026-03-12: Verification for Phase 5B: `:feature:tasks:testDebugUnitTest`, `:feature:map:compileDebugKotlin`, `testDebugUnitTest`, and `assembleDebug` passed; `enforceRules` still fails only on the pre-existing `MapScreenViewModel.kt` line-budget gate.
  - 2026-03-12: Post-Phase 5B compile measurements:
    - one-line `feature:tasks` task-editor leaf edit -> `./gradlew :feature:tasks:compileDebugKotlin` about `34.3s`
    - one-line retained `feature:map` task-shell leaf edit -> `./gradlew :feature:map:compileDebugKotlin` about `48.3s`
    - interpretation: task editor UI edits now stay in `feature:tasks`; retained map shell edits are still expensive, but materially cheaper than the post-Phase 5A sample
  - 2026-03-12: Phase 5C implemented; moved the remaining pure task panel helper UI (`TaskFilesTab`, `TaskBottomSheetRows`, `TaskBottomSheetComponents`, `QrTaskDialogs`) into `feature:tasks` while keeping `TaskTopDropdownPanel`, `SwipeableTaskBottomSheet`, and task-manage/map-runtime shells in `feature:map`.
  - 2026-03-12: Verification for Phase 5C: `:feature:tasks:compileDebugKotlin`, `:feature:map:compileDebugKotlin`, `testDebugUnitTest`, and `assembleDebug` passed; `enforceRules` still fails only on the pre-existing `MapScreenViewModel.kt` line-budget gate.
  - 2026-03-12: Post-Phase 5C shell-reduction follow-up implemented; extracted the duplicated task category-tab/body host into `feature:tasks` and rewired `TaskTopDropdownPanel` plus `SwipeableTaskBottomSheet` to keep only wrapper-state and map-owned manage-shell routing in `feature:map`.
  - 2026-03-12: Verification for the shared task-panel host extraction: `:feature:tasks:compileDebugKotlin` and `:feature:map:compileDebugKotlin` passed before full-gate reruns.
  - 2026-03-12: Focused `MapBottomSheetTabs` shell reduction implemented; moved the RainViewer and SkySight bottom-tab bodies into `feature:weather` and `feature:forecast` public content hosts while keeping tab selection, sheet visibility, and the OGN/Map4 map tabs in `feature:map`.
  - 2026-03-12: Verification for the `MapBottomSheetTabs` shell reduction: `:feature:weather:compileDebugKotlin`, `:feature:forecast:compileDebugKotlin`, `:feature:map:compileDebugKotlin`, `testDebugUnitTest`, and `assembleDebug` passed; `enforceRules` still fails only on the pre-existing `MapScreenViewModel.kt` line-budget gate.
  - 2026-03-12: Focused repass on `MapScreenContentRuntimeSections.kt` found the next safe shell-reduction seam is the forecast-owned auxiliary UI around the live map shell, not a whole-file move.
    - `MapBottomTabsSection` remains map-owned orchestration because it still coordinates traffic-detail dismissal, sheet visibility, and satellite-style switching.
    - `MapAuxiliaryPanelsAndSheetsSection` is mixed: map-screen projection, wind-tap placement, and QNH dialog flow stay in `feature:map`, but `ForecastPointCalloutCard`, `ForecastQueryStatusChip`, and the wind-speed label UI are forecast-owned presentation.
    - `WeGlideUploadPromptDialogHost` is a valid later seam, but only after `WeGlideUploadPromptUiState` moves out of `feature:map/MapScreenContract.kt`; moving the dialog alone now would create a reverse dependency or duplicate model.
    - `ForecastOverlayBottomSheetRuntime.kt` currently has no live call sites, so it should not drive the next compile-speed slice.
  - 2026-03-12: Phase 6B implemented; moved `WeGlideUploadPromptUiState` and `WeGlideUploadPromptDialogHost` into `feature:weglide` while keeping live prompt collection and map-shell rendering callsites in `feature:map`.
  - 2026-03-12: Phase 6B added owner-module unit coverage for prompt message formatting in `feature:weglide`.
  - 2026-03-12: Phase 6B deep repass found residual compile-boundary coupling still worth documenting:
    - `WeGlideUploadPromptUiState` is now owned by `feature:weglide`, but it is still threaded through the retained `feature:map` shell via `MapUiState`, `MapScreenScaffoldInputModel`, and `MapScreenContentRuntime*`
    - implementation-only edits inside `WeGlideUploadPromptDialogHost` stay isolated to `feature:weglide`, but ABI changes to `WeGlideUploadPromptUiState` still force `feature:map` recompilation because the type crosses the map shell boundary
    - `localFlightId` is currently unused by the extracted dialog host and owner-side tests; it remains in the UI state only because prompt identity is still resolved from the coordinator on the map side
    - `MapScreenViewModel` still performs inline domain-to-UI mapping from `WeGlidePostFlightUploadPrompt` to `WeGlideUploadPromptUiState`; if the prompt shape changes again, a future cleanup should centralize that mapper in `feature:weglide` or reduce the UI state surface further
    - current Phase 6B tests only cover prompt-message formatting; there is still no direct coverage for map-side confirm/dismiss wiring or notification content behavior
    - the owner-side prompt ABI currently fans out across more map-shell files than necessary (`MapScreenContract`, `MapScreenScaffoldInputModel`, `MapScreenScaffoldInputs`, `MapScreenContentRuntime`, `MapScreenContentRuntimeSections`); a future cleanup could narrow the prompt slot closer to the auxiliary section instead of threading it through the whole scaffold input model
    - `feature:weglide` still has duplicated prompt wording logic: `buildWeGlideUploadPromptMessage(...)` for the dialog host and `buildContentText(...)` inside `WeGlidePostFlightPromptNotificationController`; if the prompt copy changes, both paths need to be kept in sync
  - 2026-03-12: Phase 6B follow-up implemented for compile-speed:
    - removed `WeGlideUploadPromptUiState` from `MapUiState` and the generic scaffold input model
    - `MapScreenRoot` now collects the owner-side prompt separately and passes it only through the retained render chain (`MapScreenScaffold` -> `MapScreenContentRuntime` -> `MapScreenContentRuntimeSections`)
    - removed unused `localFlightId` from `WeGlideUploadPromptUiState`
    - centralized the domain-to-UI prompt mapper inside `feature:weglide`
    - residual coupling is now limited to the retained map render chain rather than the broader map state/scaffold-input path
  - 2026-03-12: Phase 6A repass refined the exact no-churn boundary:
    - move `ForecastPointCalloutCard`, `ForecastQueryStatusChip`, and `WindArrowSpeedTapLabel` into `feature:forecast`
    - move their forecast-owned formatting helpers with them:
      - `formatCoordinate`
      - `formatDirectionDegrees`
      - `formatWindSpeedForTap`
    - keep `WindArrowTapCallout`, the wind-tap timeout, and map-style constants in `feature:map`
    - leave the dead `ForecastOverlayBottomSheet` facade/runtime path alone in this slice to avoid unnecessary rule/doc churn
    - use a low-churn test path for the moved forecast auxiliary hosts:
      - prefer helper-level unit coverage if no Compose test behavior is changed
      - only add Compose unit-test wiring to `feature:forecast` if the moved hosts need direct render assertions
  - 2026-03-12: Phase 6A implemented; moved the forecast point-callout card, query-status chip, wind-speed tap label, and their formatting helpers into `feature:forecast` while keeping tap state, placement math, style constants, and runtime orchestration in `feature:map`.
  - 2026-03-12: Phase 6A added owner-module helper coverage in `feature:forecast` for coordinate, direction, and wind-speed formatting.
  - 2026-03-12: Verification for Phase 6A: `:feature:forecast:testDebugUnitTest`, `:feature:map:compileDebugKotlin`, `testDebugUnitTest`, and `assembleDebug` passed; `enforceRules` still fails only on the pre-existing `MapScreenViewModel.kt` line-budget gate.
  - 2026-03-12: Phase 6C repass found the current â€œremaining shell reviewâ€ definition is too broad for a no-churn compile-speed slice.
    - the next safe seam is the retained auxiliary overlay chain, not generic `screens/navdrawer`, `hawk`, `glider`, or other peripheral packages
    - `MapScreenScaffold` still imports `WeGlideUploadPromptUiState` only to pass it through to `MapScreenContent`
    - `MapScreenContentRuntime` still threads the WeGlide prompt and QNH callbacks only to reach `MapAuxiliaryPanelsAndSheetsSection`
    - `MapScreenScaffoldInputModel` still carries QNH callbacks even though only the auxiliary overlay path consumes them
    - `MapBottomTabsSection` remains mixed map-owned orchestration and is not the next no-churn target
    - `MapScreenRootHelpers`, `MapScreenBindings`, and `MapScreenManagers` are not driving the current compile-speed bottleneck and should stay out of the next slice
  - 2026-03-12: Additional Phase 6C repass findings:
    - `MapScreenContentOverlays.kt` is only a thin `QnhDialogHost` wrapper; the real QNH dialog UI still lives in `MapScreenSections.kt`, so touching overlays alone will not localize QNH ownership
    - `MapBottomTabsSection` is still the source of the `openQnhDialog` trigger, so `showQnhDialog` / `qnhInput` / `qnhError` state cannot be blindly removed from `MapScreenContentRuntime` in this phase
    - the low-churn path is to introduce a dedicated auxiliary overlay input/state seam inside `feature:map/ui`, not to widen the generic scaffold input model again
    - `MapScreenScaffoldInputs.kt` also participates in the current auxiliary callback fan-out and belongs in the Phase 6C file set
  - 2026-03-12: Additional Phase 6C deep repass findings:
    - `MapScreenScaffoldInputs.kt` and the generic scaffold input model are already prompt-clean; the remaining churn there is QNH callback fan-out, not `WeGlideUploadPromptUiState`
    - `MapScreenScaffold.kt` cannot stop importing `WeGlideUploadPromptUiState` without adding a small render seam such as an auxiliary-host lambda or content slot; editing inputs alone will not remove the prompt ABI from that wrapper
    - the main compile seam inside `MapScreenContentRuntime.kt` is the long per-field pass-through into `MapAuxiliaryPanelsAndSheetsSection`; a dedicated `feature:map/ui` auxiliary state/callback holder should replace that fan-out while keeping `openQnhDialog` wired from `MapBottomTabsSection`
  - 2026-03-12: Phase 6C implemented with the lowest-churn seam:
    - `MapScreenScaffold` now acts as a plain drawer/loading shell with a content slot and no `WeGlideUploadPromptUiState` import
    - `MapScreenScaffoldContentHost` was added as the prompt-bearing bridge from scaffold inputs into `MapScreenContent`
    - `MapAuxiliaryPanelsInputs` now localizes the auxiliary prompt/QNH payload passed into `MapAuxiliaryPanelsAndSheetsSection`
    - `MapScreenContentOverlays.kt` and `MapScreenSections.kt` were left unchanged because the QNH dialog behavior did not need to move to achieve the compile-speed seam
  - 2026-03-12: Verification for Phase 6C: `:feature:map:compileDebugKotlin` passed after the auxiliary seam refactor.
  - 2026-03-12: Phase 6D implemented with the weather-pattern shell split:
    - `ForecastSettingsScreen.kt` in `feature:map` now keeps only route fallback, top-bar, and navigation-shell behavior
    - the forecast-owned settings body moved into `feature:forecast` as a nav-free `ForecastSettingsContent`
    - `AppNavGraph.kt` and `SettingsDfRuntimeRouteSubSheets.kt` continue to route through the unchanged `ForecastSettingsScreen` entrypoint
  - 2026-03-12: Verification for Phase 6D: `:feature:forecast:compileDebugKotlin` and `:feature:map:compileDebugKotlin` passed before full verification reruns.
  - 2026-03-12: Post-Phase 6D timing pass on the remaining `feature:map` shell showed no single magical hotspot remains:
    - warm no-change `:feature:map:compileDebugKotlin` baseline: about `1.4s`
    - one-line edit in `map/ui/task/MapTaskScreenUi.kt`: about `44.3s`
    - one-line edit in `map/ui/MapBottomSheetTabs.kt`: about `49.5s`
    - one-line edit in `map/ui/MapScreenContentOverlays.kt`: about `49.1s`
    - one-line edit in `screens/navdrawer/Task.kt`: about `49.9s`
    - interpretation: the remaining cost is now mostly the retained `feature:map` module boundary itself, not a single standout file
  - 2026-03-12: Deep repass after the post-Phase 6D timing run confirmed the remaining inner-loop cost is now dominated by the retained `feature:map` module boundary, not by one last small UI seam.
    - the former `Phase 6E` minimized-indicator move is therefore deleted from the plan as too low leverage for the current goal
    - the real next lever is a release-grade `Phase 7` split of the retained `feature:map` shell from the heavy map runtime implementation
    - the main runtime factory seam is already visible in `MapScreenManagers.kt`, which constructs `MapOverlayManager`, `MapTaskScreenManager`, `MapCameraManager`, `LocationManager`, `MapLifecycleManager`, `MapInitializer`, and `TaskRenderSyncCoordinator`
    - the heaviest remaining runtime files include `MapOverlayManagerRuntimeForecastWeatherDelegate.kt` (`483` lines), `MapScreenContentRuntime.kt` (`473`), `MapOverlayManagerRuntime.kt` (`307`), `MapOverlayStack.kt` (`291`), `MapScreenContentRuntimeSections.kt` (`212`), and `MapScreenContentRuntimeEffects.kt` (`198`)
    - `MapScreenRoot.kt`, `MapScreenScaffold*`, and route shells remain the best shell anchor; they can depend on a runtime module without owning its implementation
    - `feature:map` still defines `OPENSKY_CLIENT_ID` / `OPENSKY_CLIENT_SECRET` `BuildConfig` fields, but the current module-code scan did not find live `feature:map` code references, so Phase 7 should not duplicate those fields into a runtime module unless code ownership proves they are needed

## 1) Scope

- Problem statement:
  - `feature:map` is currently the main inner-loop compile bottleneck.
  - The module contains about `717` Kotlin files and about `84,988` lines.
  - A warm no-change rerun of `./gradlew :feature:map:assembleDebug` is healthy at about `4s`.
  - A one-line leaf Kotlin edit in `feature:map` currently triggers `:feature:map:kspDebugKotlin` plus `:feature:map:compileDebugKotlin` and took about `69s`.
  - Post-Phase 1 measurement after removing unused Room KSP improved the same one-line leaf compile sample to about `65.4s`; the dominant hot path remains `:feature:map:kspDebugKotlin` plus `:feature:map:compileDebugKotlin`.
  - The same one-line edit followed by `./gradlew :feature:map:assembleDebug` took about `95s`.
- Why now:
  - The repo is still in active development.
  - Developer speed is currently constrained more by `feature:map` incremental compilation than by rule-gate wiring.
  - Reducing inner-loop compile cost is now higher-value than further gate rationalization.
- In scope:
  - Release-grade module extraction from `feature:map`
  - Low-churn dependency cleanup
  - Removal of unused annotation processors when proven safe
  - Compile-time measurement using standard Gradle commands only
- Out of scope:
  - Product behavior changes
  - UI redesign
  - Sensor/replay algorithm changes
  - One-off benchmark scripts or ad hoc build tooling
  - Big-bang module renames
- User-visible impact:
  - None intended
  - This plan is a structure/build-speed refactor, not a feature change

### 1.1 Baseline Size Findings

Top-level `feature:map` package concentration:

| Package | Kotlin files | Total lines |
|---|---:|---:|
| `tasks` | 220 | 30,853 |
| `map` | 214 | 23,336 |
| `screens` | 83 | 10,510 |
| `sensors` | 47 | 4,916 |
| `weather` | 24 | 2,421 |
| `forecast` | 17 | 2,406 |

Subpackage concentration:

| Package | Subpackage | Kotlin files | Total lines |
|---|---|---:|---:|
| `tasks` | `aat` | 94 | 13,306 |
| `tasks` | `racing` | 61 | 9,124 |
| `map` | `ui` | 60 | 7,474 |
| `screens` | `navdrawer` | 58 | 7,055 |
| `weather` | `wind` | 14 | 1,248 |
| `weather` | `rain` | 8 | 598 |
| `weather` | `ui` | 2 | 575 |

Annotation-processing findings:

- `feature:map` applies:
  - `ksp(libs.hilt.compiler)`
  - `ksp(libs.androidx.hilt.compiler)`
- `feature:map` contains a large Hilt surface (`@HiltViewModel`, `@Module`, `@InstallIn`, `@Inject` hits across `di`, `map`, `screens`, `tasks`, `forecast`, and others).
- Phase 1 removed unused Room runtime and Room KSP from `feature:map`; the remaining compile hotspot is still the large Hilt/KSP surface.

Phase 2A ownership findings:

- Forecast foundations are already split across modules:
  - `feature:profile` currently owns forecast models, settings, region utilities, preferences, and reusable forecast controls UI.
  - `feature:map` currently owns forecast auth/network/provider/runtime/view-model/settings-screen code plus map-specific forecast rendering/runtime glue.
- Because of that split, a release-grade Phase 2 cannot be â€œmove `feature:map/forecast/*` only.â€
- Phase 2A is therefore an ownership/bootstrap phase first, not a direct compile-speed phase.

## 2) Architecture Contract

### 2.1 SSOT Ownership

Runtime SSOT ownership must not change as a side effect of compile-speed refactors.

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Flight sample and live/replay display state | `FlightDataRepository` | `Flow` / `StateFlow` | Mirror repositories inside extracted leaf modules |
| Task state and task persistence authority | `TaskRepository` + task engines | use-cases / view-model state | Task state copies inside map shell |
| Forecast overlay state | `ForecastOverlayRepository` | use-cases / view-model state | Duplicate forecast state in `feature:map` shell |
| Weather rain overlay state | weather overlay repository/view-model owners | use-cases / view-model state | Separate rain state copies in map shell |
| Replay session state | `IgcReplayController` runtime | ports / flows / state | Secondary replay controllers |

The main plan invariant is:

- move code ownership between modules
- do not duplicate authoritative runtime state

### 2.2 Dependency Direction

Dependency flow must remain:

`UI -> domain -> data`

Module-level direction after extraction:

- `app` remains the top-level aggregator
- `feature:map` remains the compatibility shell to avoid churn
- new leaf modules may be depended on by `feature:map`
- `feature:profile` may depend on a new leaf module if that module becomes the canonical owner of shared forecast code
- extracted leaf modules must not depend back on `feature:map`
- no feature-to-feature cycles are allowed

Boundary risk:

- `tasks` is large but relatively cohesive; extraction is high-value but more invasive
- `forecast` is the cleanest seam
- `weather` must be split into `rain` versus `wind`; `wind` is coupled into sensors/replay and should not be moved blindly

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Forecast foundations (`models`, `settings`, `region`, `preferences`, reusable forecast controls UI`) | `feature:profile/forecast` + forecast UI helpers | new `feature:forecast` in Phase 2A | Remove split ownership first; keep package names stable and avoid cycles | compile + unit tests + full gates |
| Forecast auth/provider/runtime/view-model/settings slice | `feature:map/forecast` + forecast DI files + forecast settings UI slice | new `feature:forecast` in Phase 2B | This is the real `feature:map` compile-surface reduction for forecast | compile sample + unit tests + full gates |
| Weather rain overlay state, rain metadata, rain settings VM/use-case/UI slice | `feature:map/weather/rain` + `feature:map/weather/ui` + weather settings VM/use-case slice + `feature:profile/weather/rain` foundations | new `feature:weather` | Rain is separable from wind; move the cleaner weather surface first while retaining the map-shell wrappers | compile sample + UI regression tests + full gates |
| Embedded IGC use-cases and metadata sources currently under `feature:map` | `feature:map/igc` | existing `feature:igc` | Existing feature module should own IGC concerns | IGC tests + full gates |
| Task engines, repositories, task use-cases, task view-models, task UI | `feature:map/tasks` + `screens/navdrawer/tasks` | new `feature:tasks` | Biggest compile win; removes the largest cohesive slice from `feature:map` | compile sample + task tests + full gates |
| Map shell, MapLibre runtime, map overlay rendering, map-root orchestration | `feature:map` | stays in `feature:map` | Keep public module identity stable and avoid app-wide churn | assemble + targeted UI verification |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `map/ui/MapScreenContentRuntime.kt` | Forecast/weather view-model types are resolved inside the same giant module | Keep callsite pattern, but source the view-model types from extracted leaf modules | Phase 2B-3 |
| `map/ui/task/MapTaskScreenUi.kt` | Task UI consumes task view-model from the same giant module | Keep callsite contract, but move task types to `feature:tasks` | Phase 5 |
| `feature:map/di/*Task*.kt` and `feature:map/di/*Forecast*.kt` | DI bindings live in the monolith regardless of owner | Move DI files with the owned slice | Phase 2B and Phase 5 |

No runtime shortcut or escape-hatch behavior should be added for the sake of the refactor.

### 2.3 Time Base

This plan is not a time-base refactor. Existing time ownership must remain unchanged.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Flight metrics elapsed time and sensor cadence | Monotonic | Existing domain/fusion contract must remain intact |
| Replay playback timestamp and interpolation progress | Replay | Replay determinism must not change during module moves |
| User/profile/settings persistence timestamps when present | Wall | Persistence metadata remains wall-clock oriented |

Explicitly forbidden:

- Monotonic vs wall comparison
- Replay vs wall comparison
- Introducing direct system time into moved domain/fusion code

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged by default; module extraction must preserve current dispatcher contracts
- Primary cadence/gating sensor:
  - unchanged; sensor/replay cadence is not being redesigned here
- Hot-path latency budget:
  - unchanged for runtime behavior
  - new build-speed budget is declared separately below

### 2.5 Replay Determinism

- Deterministic for same input: Yes, must remain yes
- Randomness used: no new randomness allowed
- Replay/live divergence rules:
  - unchanged
  - extraction must not alter replay controller authority or replay state flow ownership

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Direct time usage introduced while moving code | `ARCHITECTURE.md`, `CODING_RULES.md` time rules | `enforceRules` | `archGate` + full repo gate |
| Task boundary regressions during extraction | task/map architecture rules | `enforceRules` + `test` | existing task gate rules + task unit tests |
| Replay ownership drift while moving IGC/replay-adjacent code | replay determinism contract | `test` + `review` | replay tests in `feature:igc` / map replay tests |
| Feature cycle introduced by new modules | dependency direction contract | `review` + `test` | Gradle sync + compile/assemble |
| Hilt graph break after moving DI-owned classes | DI boundary rules | `test` + `review` | compile + assemble + injected VM smoke paths |
| Compile-speed improvement claimed without measurement | build-speed objective | `review` | standard Gradle timing samples recorded in plan/PR |

### 2.7 Visual UX SLO Contract

This plan does not intend to change user-visible map behavior.

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| No user-visible regression during compile-only refactor phases | N/A | current production behavior | no regression | unit tests + unchanged screen flows | Every phase |
| If a phase changes map/task/overlay/replay runtime wiring, impacted MapScreen SLOs must be enumerated from the current matrix | impacted `MS-UX-*` / `MS-ENG-*` only when applicable | existing evidence | no regression | `docs/MAPSCREEN/02...` and `docs/MAPSCREEN/04...` evidence | Runtime-touching phases only |

### 2.8 Build Performance Contract

This is the primary objective of the plan.

Measurement rules:

- use standard Gradle commands only
- use a warm daemon and warm configuration cache
- use a one-line leaf Kotlin edit for incremental measurement
- do not add new ad hoc benchmark scripts

| Scenario | Current baseline | Target |
|---|---:|---:|
| `:feature:map:assembleDebug` no-change warm rerun | about `4s` | stay at `<= 5s` |
| `:feature:map:compileDebugKotlin` after a one-line leaf edit | about `69s` | `<= 30s` after major extractions |
| `:feature:map:assembleDebug` after a one-line leaf edit | about `95s` | `<= 45s` after major extractions |
| New extracted leaf module compile after a one-line leaf edit | N/A | `<= 15s` |

## 3) Data Flow (Before -> After)

Runtime flow should remain the same:

```
Source -> Repository (SSOT) -> UseCase -> ViewModel -> UI
```

Build/compile flow is what changes.

Before:

```
Leaf edit inside feature:map
-> :feature:map:kspDebugKotlin
-> :feature:map:compileDebugKotlin
-> optional Java/ASM/AAR tasks
-> slow dev loop even for local implementation edits
```

After:

```
Leaf edit in extracted module (forecast / weather / tasks / IGC-owned slice)
-> only that module's KSP/compile work
-> feature:map recompiles only when public ABI requires it
-> map shell remains smaller and faster for map-only edits
```

Key non-goal:

- do not rename `feature:map`
- shrink it into a shell/integration module instead

## 4) Implementation Phases

### Phase 0: Baseline Lock and Guardrails

- Goal:
  - record the real compile bottleneck and keep the objective explicit
- Files to change:
  - this plan doc only
- Tests to add/update:
  - none
- Exit criteria:
  - baseline measurements are captured
  - target compile budget is declared
  - no runtime ownership changes are proposed without SSOT mapping

### Phase 1: Immediate Compile Hygiene

- Goal:
  - remove obvious unnecessary compile work before module extraction
- Files to change:
  - [feature/map/build.gradle.kts](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/build.gradle.kts)
  - any docs that define the fast dev compile path
- Planned work:
  - confirm `feature:map` truly has no Room annotations
  - remove `ksp(libs.androidx.room.compiler)` from `feature:map` if safe
  - keep `dev-fast.bat feature:map compile` as the canonical inner-loop command
- Tests to add/update:
  - none required before the processor removal
- Exit criteria:
  - full gates pass
  - compile sample is rerun and compared to baseline

### Phase 2A: Bootstrap `feature:forecast` as the Shared Forecast Owner

- Status:
  - Implemented on 2026-03-12

- Goal:
  - remove split forecast ownership without touching the map runtime slice yet
- Files to change:
  - new `feature/forecast` module
  - `settings.gradle.kts`
  - relevant root/module Gradle dependencies
  - move `feature:profile/forecast/*`
  - move `feature:profile/map/ui/ForecastOverlayControlsContent.kt`
  - move `feature:profile/map/ui/ForecastOverlayFormatting.kt`
  - move tests that directly target the moved shared forecast foundations
- Tests to add/update:
  - relocate shared forecast unit tests with the shared owner module
  - keep profile snapshot/restore tests in `feature:profile`, updated to depend on the new owner module
- Exit criteria:
  - shared forecast code no longer lives in `feature:profile`
  - `feature:map` and `feature:profile` both depend on `feature:forecast` without cycles
  - `app` remains unchanged in this phase
  - full gates pass

Expected build impact:

- neutral to slight improvement only
- this phase is ownership/bootstrap work, not the main compile-speed win

### Phase 2B: Move the Map-Owned Forecast Slice Out of `feature:map`

- Status:
  - Implemented on 2026-03-12

- Goal:
  - remove the map-agnostic forecast implementation and its Hilt/KSP owners from `feature:map` while leaving the map/runtime compatibility shell in place
- Phase 2B repass findings:
  - the entire `feature:map/src/main/java/com/example/xcpro/forecast` package is map-agnostic and should move as a unit instead of being split internally
  - that package is about `17` production files / `2406` lines today, and it is the main forecast business/runtime surface still forcing `feature:map` recompilation
  - Phase 2B should also move `ForecastSettingsUseCase`, `ForecastSettingsViewModel`, and the three forecast DI files; together that adds about `5` more production files / `369` lines plus the secret/config owner
  - `ForecastSettingsScreen` is still called directly from [AppNavGraph.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/app/src/main/java/com/example/xcpro/AppNavGraph.kt) and from the map-owned local sub-sheet host, so moving the screen in this phase would force a new app dependency and extra nav churn
  - `ForecastOverlayBottomSheetRuntime.kt`, `SkySightUiMessagePolicy.kt`, `MapScreenContentRuntime*`, and the MapLibre overlay classes are the correct compatibility shell to keep in `feature:map`
  - `feature:forecast` does not yet own the runtime/bootstrap pieces required for this move:
    - `SKYSIGHT_API_KEY` `BuildConfig` field
    - `ForecastModule`, `ForecastNetworkModule`, and `ForecastNetworkQualifiers`
    - direct dependencies for the moved runtime/auth stack (`core:common`, `core:time`, security crypto, OkHttp path, and direct lifecycle/viewmodel support)
- Phase 2B implementation notes:
  - `feature:forecast` now owns the `SKYSIGHT_API_KEY` `BuildConfig` field and the moved forecast DI/runtime stack
  - `SkySightHttpContract` is now the explicit cross-module compatibility seam because the retained `feature:map` MapLibre network shim still consumes it
- Files to change:
  - move the full `feature:map/src/main/java/com/example/xcpro/forecast/*` package into `feature:forecast`
  - move `feature:map/src/main/java/com/example/xcpro/di/ForecastModule.kt`
  - move `feature:map/src/main/java/com/example/xcpro/di/ForecastNetworkModule.kt`
  - move `feature:map/src/main/java/com/example/xcpro/di/ForecastNetworkQualifiers.kt`
  - move `feature:map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsUseCase.kt`
  - move `feature:map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt`
  - extend `feature:forecast/build.gradle.kts` so the moved runtime/auth/viewmodel slice compiles without depending on `feature:map`
- Explicitly keep in `feature:map` for this phase:
  - `screens/navdrawer/ForecastSettingsScreen.kt`
  - `screens/navdrawer/SettingsDfRuntimeRouteSubSheets.kt` forecast sub-sheet entry
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  - `map/ui/ForecastOverlayBottomSheetRuntime.kt`
  - `map/ui/SkySightUiMessagePolicy.kt`
  - `map/ui/MapScreenContentRuntime.kt`
  - `map/ui/MapScreenContentRuntimeEffects.kt`
  - `map/ui/MapScreenContentRuntimeSections.kt`
  - `map/ForecastRasterOverlay*`
  - `map/MapOverlayManagerRuntimeForecastWeather*`
  - `map/SkySightSatelliteOverlay.kt`
  - `map/SkySightMapLibreNetworkConfigurator.kt`
- Tests to add/update:
  - move the remaining forecast package unit tests from `feature:map/src/test/java/com/example/xcpro/forecast/*` into `feature:forecast`
  - move `feature:map/src/test/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModelTest.kt` with the moved settings VM/use-case owner
  - keep map UI/runtime tests in `feature:map`, including:
    - `MapBottomSheetTabsTest`
    - `MapBottomTabsLayerInstrumentedTest`
    - `SkySightUiMessagePolicyTest`
    - `SkySightSatelliteOverlay*`
    - `SkySightMapLibreNetworkConfiguratorTest`
- Exit criteria:
  - the full `feature:map/forecast` package no longer lives in `feature:map`
  - forecast DI and secret/config ownership no longer live in `feature:map`
  - `ForecastSettingsScreen` remains callable from app and the local map settings sub-sheet without app/nav rewiring
  - forecast implementation edits in the moved business/runtime slice no longer require `feature:map` compilation
  - no feature cycle is introduced
  - full gates pass

Expected build impact:

- materially better than Phase 2A
- this slice removes about `22` production files / `2775` lines plus `13` related unit tests from `feature:map`
- it also removes most remaining forecast-specific Hilt/KSP ownership from `feature:map` without touching MapLibre runtime code or app routes

### Phase 3A: Extract Weather Rain Without Pulling Wind Prematurely

- Status:
  - Implemented on 2026-03-12

- Goal:
  - remove the clean weather/rain UI and state slice while leaving wind coupled to sensors until a safer step
- Files to change:
  - new `feature/weather` module
  - move `feature:profile/weather/rain/*`
  - move `feature:map/weather/rain/*`
  - move `feature:map/weather/ui/*`
  - move `feature:map/screens/navdrawer/WeatherSettingsUseCase.kt`
  - move `feature:map/screens/navdrawer/WeatherSettingsViewModel.kt`
  - move `feature:map/di/WeatherMetadataNetworkModule.kt`
  - move `feature:map/di/WeatherNetworkQualifiers.kt`
  - retain `feature:map/weather/wind/*` in `feature:map`
  - retain map-shell weather entrypoints/runtime in `feature:map`
- Tests to add/update:
  - move weather-owned rain unit tests into `feature:weather`
  - move weather settings VM/use-case tests into `feature:weather`
  - keep wrapper/UI behavior tests in `feature:map`
- Exit criteria:
  - rain/settings edits compile in the new leaf module
  - `feature:map` no longer owns the rain overlay state/repository/view-model stack
  - drawer route and in-map weather tabs still work through retained `feature:map` compatibility shells
  - no sensor/replay drift is introduced

Phase 3A implementation notes:

- `feature:weather` now owns:
  - rain foundations and preferences previously split with `feature:profile`
  - rain metadata repository and state assembly
  - rain overlay view-model
  - shared weather settings content
  - weather settings use-case and view-model
  - weather metadata DI qualifiers/module
- `feature:map` intentionally still owns:
  - `WeatherSettingsScreen.kt`
  - `WeatherSettingsScreenRuntime.kt`
  - `SettingsDfRuntimeSheets.kt` / local map weather sub-sheet hosting
  - `MapBottomSheetTabs.kt`
  - `MapWeatherOverlayEffects.kt`
  - `WeatherRainOverlay.kt`
  - `MapOverlayManagerRuntimeForecastWeather*`
  - all `weather/wind/*` code

Expected build impact:

- meaningful for weather-owned edits, limited for map-owned edits
- this slice moves about `17` production files / `1820` lines and `13` test files / `1765` lines out of `feature:map` and `feature:profile`
- measured result:
  - one-line `feature:weather` leaf edit -> about `18.9s`
  - one-line retained `feature:map` weather-shell leaf edit -> about `71.4s`
  - interpretation: the extraction achieved the leaf-module isolation objective, but `feature:map` still needs further shrinking

### Phase 3B: Reassess Remaining Map-Weather Orchestration

- Goal:
  - decide whether any remaining map-weather orchestration should move after Phase 3A stabilizes
- Current recommendation:
  - do not move the map overlay/render shell yet
- Keep in `feature:map` unless a later focused review proves otherwise:
  - `MapWeatherOverlayEffects.kt`
  - `WeatherRainOverlay.kt`
  - `MapOverlayManagerRuntimeForecastWeather*`
  - weather routing/wrapper screens
- Exit criteria:
  - explicit decision recorded
  - no churn-only move of MapLibre/weather shell code

### Phase 4: Move Map-Owned IGC Logic to Existing `feature:igc`

- Goal:
  - remove duplicate IGC ownership from `feature:map`
- Files to change:
  - `feature/map/igc/*`
  - existing `feature/igc` module
  - any DI files that still bind IGC-owned types from `feature:map`
- Tests to add/update:
  - IGC recovery/recording tests in `feature:igc`
  - replay regression tests where affected
- Exit criteria:
  - embedded IGC use-cases no longer live under `feature:map`
  - replay/runtime contracts remain unchanged
  - full gates pass

### Phase 5A: Extract Task Core Without Pulling the Map Task Shell

Implemented 2026-03-12:

- `feature:tasks` now owns the extracted task core/runtime/state slice.
- `app` now depends directly on `feature:tasks` for singleton task providers.
- `feature:map` now depends on `feature:tasks` for retained task UI/render shells.
- The retained map shell boundary held:
  - app/nav route wrappers stayed in `feature:map`
  - map task render/edit shells stayed in `feature:map`
  - map-only task tests stayed in `feature:map`

- Goal:
  - move the task-owned core/runtime/state slice out of `feature:map` without touching app/nav wrappers or MapLibre render shells
- Files to change:
  - new `feature/tasks` module
  - move task-owned core/runtime/state files, including:
    - `feature:map/tasks/core/*`
    - `feature:map/tasks/domain/*`
    - `feature:map/tasks/data/persistence/*`
    - task coordinator/navigation/repository/view-model/use-case files
    - non-render racing/AAT engines, persistence, validators, calculators, models, and storage
    - task DI modules that belong to the moved runtime slice
  - add direct `app -> feature:tasks` dependency because `app` currently owns the singleton task providers
  - add `feature:map -> feature:tasks` dependency for retained map task shells/adapters
- Explicitly keep in `feature:map` for this phase:
  - `AppNavGraph` task route and `screens/navdrawer/Task.kt`
  - `screens/navdrawer/TaskScreenUseCasesViewModel.kt`
  - `screens/navdrawer/tasks/*`
  - `map/MapTaskScreenManager.kt`
  - `map/MapTaskIntegration.kt`
  - `map/TaskRenderSyncCoordinator.kt`
  - `map/ui/task/MapTaskScreenUi.kt`
  - `tasks/TaskMapOverlay.kt`
  - `tasks/TaskTopDropdownPanel.kt`
  - `tasks/TaskManagerCompat.kt`
  - all task render/edit files that still depend on MapLibre or `feature:map` `BuildConfig`
- Tests to add/update:
  - move task-owned coordinator/navigation/repository/view-model/domain tests with the new module
  - keep map-shell task tests in `feature:map`, including:
    - `MapTaskScreenManagerTest`
    - `MapTaskScreenUiTest`
    - `TaskRenderSyncCoordinator*`
    - IGC task declaration adapter tests
- Exit criteria:
  - task-core implementation edits compile in `feature:tasks`
  - `feature:map` no longer owns the task SSOT/coordinator/view-model/persistence layer
  - app/nav wrappers and map task render shells still compile without route churn
  - no feature cycle is introduced

Expected build impact:

- high-value, but not the full task win yet
- this slice targets the large task-owned core/runtime/state surface first while deliberately leaving map task shells in place
- `feature:map` should shrink materially, but map-owned task shell edits may still remain expensive until Phase 5B

### Phase 5B: Move Task Editor UI Atoms While Retaining the Map Task Shell

- Status:
  - Implemented on 2026-03-12

- Goal:
  - move the task editor UI atoms that are no longer map-specific into `feature:tasks` without touching app/nav wrappers or MapLibre shells
- Files moved to `feature:tasks`:
  - `tasks/SearchableWaypointField.kt`
  - `tasks/RulesBTTab*.kt`
  - `tasks/RulesRacingTaskParameters.kt`
  - `tasks/aat/AATManageList*.kt`
  - `tasks/aat/AATTargetControls.kt`
  - AAT point-type selector UI files under `tasks/aat/ui/*PointSelector*.kt`
  - `tasks/racing/RacingWaypointList*.kt`
  - racing point-type selector UI files under `tasks/racing/ui/*PointSelector*.kt`
  - `tasks/RulesRacingTaskParametersTest.kt`
- Explicitly kept in `feature:map`:
  - `TaskTopDropdownPanel.kt`
  - `SwipeableTaskBottomSheet.kt`
  - `RacingManageBTTab.kt`
  - `AATManageContent.kt`
  - `MapTaskScreenManager.kt`
  - `MapTaskIntegration.kt`
  - `TaskRenderSyncCoordinator.kt`
  - `TaskMapOverlay.kt`
  - `MapTaskScreenUi.kt`
  - `screens/navdrawer/Task.kt`
  - `screens/navdrawer/tasks/*`
  - MapLibre renderers / edit overlays / gesture shells
- Additional low-churn wiring:
  - `feature:tasks` now applies Compose and owns the moved task-editor UI test surface
  - `feature:tasks` now depends on `dfcards-library` because the shared units types still live there
  - only the cross-module entrypoint composables that the retained map shell calls were made public
- Exit criteria:
  - task editor UI edits compile in `feature:tasks`
  - retained map wrappers compile against `feature:tasks` without package churn
  - map render/nav shells remain in `feature:map`

### Phase 5C: Move Shared Task Panel Helper UI While Keeping Wrapper Shells in `feature:map`

- Status:
  - Implemented on 2026-03-12

- Goal:
  - move the remaining pure task-panel helper UI out of `feature:map` without touching the retained wrapper shells or MapLibre/task-manage routing

- Missed seam found in the repass:
  - `TaskTopDropdownPanel.kt` and `SwipeableTaskBottomSheet.kt` are mixed-ownership wrappers and should stay in `feature:map` for now
  - but they still depend on pure task-owned helper UI that remains parked in `feature:map`
  - both wrappers also duplicate the same category-tab shell structure, which should only be reduced after the helper files move first

- Files moved to `feature:tasks` in this phase:
  - `tasks/TaskFilesTab.kt`
  - `tasks/TaskBottomSheetRows.kt`
  - `tasks/TaskBottomSheetComponents.kt`
  - `tasks/QrTaskDialogs.kt`

- Ownership after this phase:
  - `feature:tasks` owns:
    - `TaskCategory`
    - `FilesBTTab`
    - `MinimizedContent`
    - `WaypointPreviewCard`
    - `TaskPreviewContent`
    - `QRCodeDialog`
  - `feature:map` keeps:
    - `TaskTopDropdownPanel.kt`
    - `SwipeableTaskBottomSheet.kt`
    - `ManageBTTabRouter.kt`
    - `RacingManageBTTab.kt`
    - `AATManageContent.kt`
    - any file that takes `MapLibreMap`, `MapTaskScreenManager`, or map-owned runtime collaborators

- Why this is the safe next slice:
  - the helper files already depend on task-owned view-model/state/share utilities
  - they have no direct `MapLibre`, `MapTaskScreenManager`, or map-runtime ownership
  - both retained wrappers and retained task-manage shells already consume these helpers through stable composable boundaries
  - `TaskPreviewContent` appears to be task-owned helper UI and can move with this batch without forcing behavior change or deletion churn

- Follow-up seam after 5C:
  - implemented on 2026-03-12: extracted the duplicated category-tab/body composable structure shared by `TaskTopDropdownPanel.kt` and `SwipeableTaskBottomSheet.kt` into a `feature:tasks` host composable
  - drag/position/panel-state behavior remains in `feature:map`

- Exit criteria:
  - helper-only task panel edits compile in `feature:tasks`
  - retained wrapper shells in `feature:map` compile with imports redirected to `feature:tasks`
  - no app/nav or map-render churn is introduced

### Phase 6: Shrink the Remaining Shell Deliberately

- Goal:
  - keep `feature:map` as a thin map-shell module, not a general dumping ground

### Phase 6A: Forecast Auxiliary Host Extraction Around `MapScreenContentRuntimeSections.kt`

- Implemented on 2026-03-12

- Goal:
  - remove forecast-owned auxiliary presentation from the live `feature:map` shell while keeping map projection, overlay runtime, and sheet orchestration in `feature:map`
- Files to change:
  - move forecast-owned UI from `feature:map` into `feature:forecast`:
    - `map/ui/ForecastPointCalloutCard`
    - `map/ui/ForecastQueryStatusChip`
    - `map/ui/WindArrowSpeedTapLabel`
    - the forecast-owned formatting helpers they depend on:
      - `formatCoordinate`
      - `formatDirectionDegrees`
      - `formatWindSpeedForTap`
  - keep in `feature:map`:
    - `MapScreenContentRuntime.kt`
    - `MapScreenContentRuntimeSections.kt`
    - `MapScreenContentRuntimeEffects.kt`
    - `MapScreenContentRuntimeSupport.kt` state/runtime pieces:
      - `WindArrowTapCallout`
      - `WIND_ARROW_SPEED_TAP_DISPLAY_MS`
      - `DEFAULT_WIND_SPEED_UNIT_LABEL`
      - `SATELLITE_MAP_STYLE_NAME`
      - `DEFAULT_NON_SATELLITE_MAP_STYLE_NAME`
    - wind-tap screen-point placement and visibility timeout orchestration
    - `QnhDialogHost`
    - leave `ForecastOverlayBottomSheet.kt` / `ForecastOverlayBottomSheetRuntime.kt` untouched in this phase; they are not the live seam and changing them now adds doc/rule churn without helping the active compile path
  - publish the moved forecast auxiliary hosts as stable public APIs in `feature:forecast`; `internal` visibility will no longer work once `feature:map` consumes them cross-module
- Why this is the safe next slice:
  - the moved UI is forecast-owned presentation with no `MapLibreMap` dependency
  - `MapAuxiliaryPanelsAndSheetsSection` can keep the map projection math and simply render feature-owned hosts
  - it shrinks the live shell around an actively used path instead of chasing older compatibility wrappers
  - `feature:forecast` already owns the `com.example.xcpro.map.ui` package for shared map-facing forecast hosts, so this follows the established no-churn package pattern
- Tests to add/update:
  - add targeted owner-module coverage for the moved auxiliary hosts
  - low-churn default:
    - add plain unit tests for the moved formatting helpers (`formatCoordinate`, `formatDirectionDegrees`, `formatWindSpeedForTap`)
  - only if direct host rendering needs protection:
    - add Compose unit-test wiring to `feature:forecast` (`ui-test-junit4` + `ui-test-manifest`) and cover one moved host render path
  - `MapBottomSheetTabsTest` only if imports or shared package hosts change again
- Exit criteria:
  - forecast auxiliary UI bodies compile in `feature:forecast`
  - `feature:map` keeps only projection/orchestration for forecast tap and query-status presentation
  - no map-runtime or style-switch behavior moves out of `feature:map`
  - implementation-only edits inside the moved forecast auxiliary UI no longer require `:feature:map:compileDebugKotlin` unless the public API changes

### Phase 6B: WeGlide Prompt Ownership Cleanup

- Implemented on 2026-03-12

- Goal:
  - move the WeGlide upload prompt dialog out of the map shell only after the prompt model has a non-map owner
- Files to change:
  - relocate `WeGlideUploadPromptUiState` out of `feature:map/MapScreenContract.kt` into `feature:weglide` or a neutral shared owner
  - move `WeGlideUploadPromptDialogHost` into `feature:weglide`
  - keep in `feature:map` only the retained map-shell callsite, prompt visibility wiring, and the minimal render-chain threading needed to reach the auxiliary panel section
- Why this is not the first Phase 6 slice:
  - the prompt model currently lives in `feature:map`, so moving the dialog first would create a reverse dependency or duplicate the model
  - compile-speed payoff is smaller than the forecast auxiliary host move
  - Tests to add/update:
    - current coverage:
      - owner-side prompt-message formatting tests in `feature:weglide`
    - remaining useful coverage if this seam changes again:
      - map-side confirm/dismiss wiring around `MapScreenViewModel`
      - notification content behavior in `WeGlidePostFlightPromptNotificationController`
      - any future prompt-copy refactor should add a shared formatter test if dialog and notification wording are unified
  - Exit criteria:
    - WeGlide prompt UI and model ownership are outside `feature:map`
    - `feature:map` passes prompt data and callbacks into the extracted host
    - accepted residual coupling for this phase:
      - the owner-side UI state type still crosses the retained map shell, so schema changes to `WeGlideUploadPromptUiState` are not yet compile-isolated from `feature:map`
      - the prompt slot still crosses the retained render chain (`MapScreenRoot`, `MapScreenScaffold`, `MapScreenContentRuntime`, `MapScreenContentRuntimeSections`) rather than being localized to the auxiliary panel section alone

### Phase 6C: Auxiliary Overlay Chain Localization

- Goal:
  - localize auxiliary-only prompt and QNH wiring so owner-side prompt ABI and auxiliary callbacks stop crossing generic shell wrappers
  - collapse the long per-field auxiliary parameter fan-out into a dedicated `feature:map/ui` seam instead of spreading future auxiliary changes across multiple signatures
- Implemented shape:
  - `MapScreenScaffold` uses a content slot instead of importing prompt-specific ABI
  - `MapScreenScaffoldContentHost` owns the prompt-bearing bridge into `MapScreenContent`
  - `MapAuxiliaryPanelsAndSheetsSection` consumes `MapAuxiliaryPanelsInputs`
  - `MapBottomTabsSection`, `QnhDialogHost`, and `QnhDialog` stay where they are
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffold.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSections.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentOverlays.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSections.kt`
  - add a dedicated auxiliary-host input/state type or sibling auxiliary host to avoid pass-through churn instead of widening the generic scaffold input model
  - if needed, add a small `MapScreenScaffold` render seam (`auxiliaryHost` lambda or equivalent content slot) so the scaffold wrapper no longer imports `WeGlideUploadPromptUiState`
- Explicitly keep unchanged in this phase:
  - `MapBottomTabsSection`
  - `MapScreenRootHelpers.kt`
  - `MapScreenBindings.kt`
  - `MapScreenManagers.kt`
  - `MapOverlayStack` / MapLibre runtime ownership
- Tests to add/update:
  - targeted owner-side tests only if prompt/QNH helper behavior changes
  - map-shell compile verification after the chain is shortened
- Exit criteria:
  - `MapScreenScaffold` no longer imports `WeGlideUploadPromptUiState`
  - `MapScreenScaffoldInputs` and the generic scaffold input model only retain the QNH callback surface still needed by the retained map shell; prompt-specific ABI does not cross them
  - `MapScreenContentRuntime` only retains the QNH state/trigger surface it directly owns from `MapBottomTabsSection`; auxiliary-only prompt wiring is pushed closer to `MapAuxiliaryPanelsAndSheetsSection`
  - `MapAuxiliaryPanelsAndSheetsSection` consumes a dedicated auxiliary input/state holder instead of a long per-field argument list
  - auxiliary-only prompt/QNH wiring is localized as close as possible to `MapAuxiliaryPanelsAndSheetsSection` without moving `MapBottomTabsSection` or MapLibre runtime ownership
  - `feature:map` retains map shell + MapLibre runtime + minimal integration glue

### Phase 6D: Forecast Settings Route Shell Reduction

- Goal:
  - convert the remaining forecast settings route in `feature:map` into a compatibility shell so forecast-owned settings UI edits stop paying `:feature:map:compileDebugKotlin`
  - follow the already-proven weather pattern instead of doing another broad shell review
- What the repass showed:
  - `WeatherSettingsScreenRuntime.kt` is already a thin compatibility shell at about `86` lines and should not drive this phase
  - `ForecastSettingsScreen.kt` is still a large owner-heavy UI surface at about `394` lines in `feature:map` even though its `ViewModel` and `UseCase` already live in `:feature:forecast`
  - the safe move is not to relocate the whole route file; `ForecastSettingsScreen.kt` still owns route fallback behavior for `NavHostController`, `DrawerState`, and `SettingsTopAppBar`, which matches the shell responsibilities intentionally retained in `feature:map`
  - the correct low-churn shape is to mirror `WeatherSettingsScreenRuntime.kt`: keep the route/sheet shell and navigation/top-bar glue in `feature:map`, and move only the forecast settings content body into `:feature:forecast`
  - `:feature:forecast` currently has no navigation-compose dependency and should not gain one for this phase; the moved owner-side UI should be a nav-free content host/body composable
  - `AppNavGraph.kt` and `SettingsDfRuntimeRouteSubSheets.kt` already route through the existing `ForecastSettingsScreen` entrypoint, so route names and call sites should remain unchanged
  - `MapBottomSheetTabs.kt`, `MapTaskScreenUi.kt`, and legacy `Task.kt` remain mixed map/runtime shells and are not low-churn Phase 6D candidates
  - `MapScreenContentOverlays.kt` is mostly thin wrappers and is lower compile-payoff than the forecast settings route
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`
  - add a forecast-owned content host / body composable in `feature/forecast/src/main/java/com/example/xcpro/screens/navdrawer/`
  - keep call sites in:
    - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
    - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/SettingsDfRuntimeRouteSubSheets.kt`
- Explicitly keep unchanged in this phase:
  - route names and nav destinations
  - `SettingsTopAppBar` ownership in `feature:map`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsScreenRuntime.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/task/MapTaskScreenUi.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Task.kt`
  - MapLibre/runtime ownership and map settings routing
- Tests to add/update:
  - targeted owner-side tests for the moved forecast settings content only
  - keep route-level policy tests in `feature:map`; do not migrate them with the content body
  - if direct compose rendering tests are needed in `:feature:forecast`, add test wiring there deliberately rather than implicitly
  - compile verification for `:feature:forecast` and `:feature:map`
- Exit criteria:
  - `ForecastSettingsScreen.kt` in `feature:map` becomes a thin compatibility shell comparable to the weather settings route
  - forecast settings content and presentation logic live in `:feature:forecast`
  - the moved forecast settings content no longer depends on `NavHostController`, `DrawerState`, or map-local route helpers
  - app nav and settings sub-sheet routes stay stable
  - `feature:map` retains only route/shell glue for forecast settings

### Phase 7: Split the Retained `feature:map` Shell from the Heavy Runtime

- Goal:
  - target the actual remaining compile bottleneck by separating the retained `feature:map` shell from the heavy map runtime implementation
  - stop treating the last few shell files as if one more small UI extraction will materially change the current `44s` to `50s` edit-compile band
- Why `Phase 6E` was deleted:
  - the minimized-indicator seam is valid but too low leverage for the stated goal
  - after Phase 6D, unrelated shell edits still clustered together:
    - `map/ui/task/MapTaskScreenUi.kt`: about `44.3s`
    - `map/ui/MapBottomSheetTabs.kt`: about `49.5s`
    - `map/ui/MapScreenContentOverlays.kt`: about `49.1s`
    - `screens/navdrawer/Task.kt`: about `49.9s`
  - that timing pattern means the dominant cost is now the retained module boundary itself, not one last local UI seam
- What the code pass showed:
  - `MapScreenManagers.kt` is the main runtime factory seam today; it constructs:
    - `MapOverlayManager`
    - `MapTaskScreenManager`
    - `MapCameraManager`
    - `LocationManager`
    - `MapLifecycleManager`
    - `MapInitializer`
    - `TaskRenderSyncCoordinator`
  - the heaviest remaining runtime-side files are already concentrated in a coherent runtime cluster:
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt` (`483` lines)
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt` (`473`)
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt` (`373`)
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt` (`307`)
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt` (`291`)
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt` (`250`)
    - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Task.kt` (`247`)
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSections.kt` (`212`)
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt` (`198`)
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenManagers.kt` (`174`)
    - `feature/map/src/main/java/com/example/xcpro/map/MapTaskScreenManager.kt` (`155`)
  - not all of those files should move in the first split:
    - `MapScreenRoot.kt`, `MapScreenScaffold*`, route shells, and compatibility wrappers are still the best shell anchor
    - `MapBottomSheetTabs.kt`, `MapTaskScreenUi.kt`, and `Task.kt` remain mixed wrappers and should stay in the shell initially unless the new module boundary proves more movement is needed
  - the current plan was too optimistic about shell-owned contracts:
    - `MapScreenState` is explicitly runtime-only MapLibre/overlay handle state and should move with the runtime, not remain in the shell
    - `MapStateReader` and `MapStateActions` are runtime contracts used by `LocationManager`, `MapCameraManager`, `MapInitializer`, and other manager classes; leaving them in the shell would create a reverse dependency
    - `MapStateStore.MapPoint`, `MapStateStore.MapSize`, and `MapStateStore.CameraSnapshot` currently leak through those runtime contracts, so Phase 7 needs a contract extraction step before manager moves
    - `MapStateReader` and `MapStateActions` also still expose shell-local contract types:
      - `DisplayPoseMode`
      - `DisplaySmoothingProfile`
      - `TrailSettings`
      Those types need a runtime-contract home or a shared contract home before `:feature:map-runtime` can depend on the extracted interfaces cleanly
    - `MapStateActionsDelegate` is an implementation detail over `MapStateStore`, not a first-move runtime file; it can remain in `feature:map` initially, but its method signatures must switch to the extracted top-level contract value types once `MapStateStore.MapPoint`, `MapStateStore.MapSize`, and `MapStateStore.CameraSnapshot` are lifted out
    - shell-side consumers such as `MapScreenRoot.kt`, `MapScreenBindings.kt`, `MapReplaySnapshotControllers.kt`, and `MapScreenViewModel.kt` already depend on the leaked `MapStateStore` value types, so 7A must budget for shell import churn even if the first implementation keeps `MapStateStore` itself in `feature:map`
    - that shell churn also reaches `MapScreenRuntimeEffects.kt` and `MapScreenViewModelStateBuilders.kt`, because they already consume contract-surface state such as `TrailSettings` and `MapSensorsUseCase`
    - `MapScreenState` is not a contract and should not be treated as one in 7A:
      - it is a concrete runtime handle container for `MapLibreMap`, overlays, controllers, and plugin instances
      - it is instantiated directly in `MapScreenRoot.kt`
      - it should move with the runtime-core/bootstrap work in 7B, not with the first interface/value-type extraction
    - `MapScreenRuntimeDependencies` is also not a clean 7A contract move:
      - it is a shell-facing grouped convenience wrapper built in `MapScreenViewModel.kt`
      - it currently carries concrete runtime collaborators such as `FlightDataManager`
      - it should stay in `feature:map` for 7A and only be narrowed or relocated after a real runtime facade exists
    - `TrailSettings` is the one remaining trail-owned type leaking through `MapStateReader`; the low-churn 7A option is to trim `trailSettings` off `MapStateReader` and thread it separately, rather than drag the broader trail package into the first contract extraction
  - the first split also has two compatibility blockers:
    - `MapTaskScreenManager` still imports `com.example.xcpro.tasks.BottomSheetState`, so that manager should stay in the shell initially unless the compatibility shim is relocated or deleted
    - `MapRuntimeController` still depends on `MapCommand`, `MapStyleUrlResolver`, and `BuildConfig.DEBUG`; it is safe to defer until those helpers have a clear runtime owner
  - `MapScreenContentRuntime.kt`, `MapScreenContentRuntimeSections.kt`, and `MapScreenScaffoldInputs.kt` are more shell-mixed than the prior plan stated:
    - they depend on `hiltViewModel()` entrypoints, feature-owned forecast/weather view-models, `FlightDataViewModel`, shell bindings, and long concrete runtime parameter lists
    - they should stay in the shell for the first runtime split and only move later if a narrower runtime facade is established
  - `feature:map` still defines `OPENSKY_CLIENT_ID` / `OPENSKY_CLIENT_SECRET` `BuildConfig` fields, but the current `feature:map` code scan did not find live module-code references; Phase 7 should not duplicate that config into a new runtime module unless a concrete runtime owner is identified during implementation
- Proposed module shape:
  - keep `:feature:map` as the stable shell / compatibility module
  - add `:feature:map-runtime` for heavy MapLibre, overlay, manager, and runtime orchestration code
- Keep in `:feature:map` during the first runtime split:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreen.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffold.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldContentHost.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSections.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentOverlays.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSections.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenContract.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel*.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapStateStore.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapStateActionsDelegate.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenRuntimeDependencies.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt` shell-owned view-model/use-case wrappers outside the runtime-owned subset
  - `feature/map/src/main/java/com/example/xcpro/map/MapRuntimeController.kt`
  - route and settings shells such as:
    - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Task.kt`
    - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`
    - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsScreenRuntime.kt`
  - mixed shell wrappers kept stable for the first split:
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/task/MapTaskScreenUi.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`
- Move into `:feature:map-runtime` in the first split:
  - runtime contracts and runtime-owned grouped dependencies first:
    - `feature/map/src/main/java/com/example/xcpro/map/MapStateReader.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapStateActions.kt`
    - top-level replacements or extracted homes for:
      - `MapStateStore.MapPoint`
      - `MapStateStore.MapSize`
      - `MapStateStore.CameraSnapshot`
      - `DisplayPoseMode`
      - `DisplaySmoothingProfile`
      - any remaining contract-surface types currently sourced from the shell, excluding trail-owned types that are intentionally trimmed off the contract for 7A
    - the runtime-owned use-case subset from `MapScreenUseCases.kt`:
      - `MapSensorsUseCase`
      - `MapTasksUseCase`
  - manager/runtime construction and runtime orchestration after the contract extraction:
    - `feature/map/src/main/java/com/example/xcpro/map/MapScreenState.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenManagers.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficOverlayUiAdapters.kt`
  - direct map runtime implementations:
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager*.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayRuntime*.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer*.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapCamera*.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapLifecycleManager.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay*.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt`
  - defer these until after the initial runtime split proves stable:
    - `feature/map/src/main/java/com/example/xcpro/map/MapTaskScreenManager.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime*.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapWeatherOverlayEffects.kt`
  - move additional direct MapLibre helpers only when imports prove they are runtime-owned and the shell does not need to edit them frequently
- Implementation order:
  - Phase 7A: runtime contract extraction
    - Implemented on 2026-03-12 as an internal low-churn extraction within `feature:map`; this phase intentionally did not create `:feature:map-runtime` yet
    - extract the runtime-side contracts out of the shell first:
      - `MapStateReader`
      - `MapStateActions`
      - the runtime-owned subset of `MapScreenUseCases.kt`
    - replace the nested `MapStateStore` DTO contract surface with top-level runtime/shared types:
      - `MapStateStore.MapPoint`
      - `MapStateStore.MapSize`
      - `MapStateStore.CameraSnapshot`
    - move or re-home every other contract type currently leaking from the shell:
      - `DisplayPoseMode`
      - `DisplaySmoothingProfile`
      - trim `TrailSettings` off `MapStateReader`, or deliberately re-home a narrow trail-settings contract if trimming is not viable
    - keep `MapStateStore` and `MapStateActionsDelegate` in `feature:map` initially; they are shell-side implementations that can adopt the extracted contract types without being first-move runtime files
    - keep `MapScreenState` in `feature:map` during 7A; it is a concrete runtime state container and moves with the runtime-core/bootstrap work in 7B
    - keep `MapScreenRuntimeDependencies` in `feature:map` during 7A; it is a shell-facing grouping over concrete collaborators, not a stable first-step contract
    - expect limited shell churn here:
      - `MapScreenRoot.kt`
      - `MapScreenBindings.kt`
      - `MapReplaySnapshotControllers.kt`
      - `MapScreenViewModel.kt`
      - `MapScreenRuntimeEffects.kt`
      - `MapScreenViewModelStateBuilders.kt`
      These files already consume the leaked contract types and will need import/type updates even if their ownership does not change
  - Phase 7B: `:feature:map-runtime` bootstrap and manager/runtime-core extraction
    - Implemented bootstrap slice on 2026-03-12:
      - created `:feature:map-runtime` with the minimum Android/MapLibre/Hilt/KSP configuration needed for the extracted runtime-facing contracts and task runtime bridge
      - kept package names stable so `feature:map` shell imports changed only through Gradle/module ownership, not package rename churn
      - moved into `:feature:map-runtime` in the bootstrap slice:
        - `MapStateReader`
        - `MapStateActions`
        - `MapStateModels.kt` (`MapPoint`, `MapSize`, `CameraSnapshot`)
        - `DisplayPoseMode`
        - `DisplayPoseSmoothingConfig`
        - `DisplaySmoothingProfile`
        - `MapFeatureFlags`
        - `MapUiModels.kt`
        - `MapTasksUseCase`
        - `TaskRenderSyncCoordinator`
      - explicitly kept `MapSensorsUseCase` in `feature:map` because `VarioServiceManager` still lives in `feature:map`
      - narrowed `TaskRenderSyncCoordinator` so the shell injects `TaskMapRenderRouter` explicitly; this avoids a reverse dependency from `:feature:map-runtime` back to `feature:map`
    - 2026-03-12 focused 7B.1 repass: the originally proposed first manager move set is still too coarse for a no-churn slice
      - `MapCameraManager`, `LocationManager`, `SnailTrailManager`, and `MapLifecycleManager` all still depend directly on the concrete `MapScreenState`
      - moving those classes as-is would either force a wholesale `MapScreenState` move too early or recreate a shell/runtime reverse dependency
      - `LocationManager` is the biggest hidden coupling point:
        - it still depends on a local helper cluster that remains in `feature:map`:
          - `LocationSensorsController`
          - `MapCameraPreferenceReaderAdapter`
          - `MapLibreCameraControllerProvider`
          - `MapScreenSizeProvider`
          - `MapLocationFilter`
          - `MapCameraUpdateGateAdapter`
          - `MapPositionController`
          - `MapUserInteractionController`
          - `MapTrackingCameraController`
          - `DisplayPoseRenderCoordinator`
          - `RenderFrameSync`
        - it still references `com.example.xcpro.map.BuildConfig.DEBUG`
        - it still owns the nested `DisplayPoseSnapshot` type that shell effects consume
      - `DisplayPoseRenderCoordinator` is still coupled to `LocationManager.DisplayPoseSnapshot`, so a top-level runtime-owned snapshot contract is needed before `LocationManager` can move cleanly
      - `SnailTrailManager` is still coupled to:
        - `MapScreenState.snailTrailOverlay`
        - concrete `SnailTrailOverlay`
        - trail runtime state cached on `MapScreenState`
        so it should move with the trail overlay/runtime cluster or behind a narrower runtime-handles port
      - `MapLifecycleManager.kt` still mixes two ownerships in one file:
        - runtime lifecycle / overlay cleanup logic
        - `MapLifecycleEffects` Compose bridge
        it must be split before any move
      - `rememberMapScreenManagers(...)` cannot delegate to a moved aggregate unchanged because the current `MapScreenManagers` data class still includes shell-only managers:
        - `MapUIWidgetManager`
        - `MapModalManager`
        - `MapTaskScreenManager`
    - no-churn 7B.1 is therefore narrowed to runtime-prep work before the first real manager move:
      - extract a top-level runtime-owned `DisplayPoseSnapshot`
      - split `MapLifecycleManager.kt` into:
        - runtime `MapLifecycleManager`
        - shell `MapLifecycleEffects`
      - introduce a non-Compose runtime manager factory / aggregate that excludes shell-only managers
      - only after that, reassess the first concrete runtime-class move
      - first likely class after the prep split: `MapCameraManager`
      - keep `LocationManager` and `SnailTrailManager` deferred until their helper/overlay clusters are untangled
    - additional 2026-03-12 repass findings for 7B.1:
      - `MapCameraManager` still leaks `MapScreenState` directly through `internal val mapState`, and shell code already relies on that leak in `MapCameraEffects.kt`
        - that means a plain file move is still not enough
        - the class needs a narrower shell-facing API before it can move cleanly
      - `MapLifecycleManager` runtime class still depends on concrete `LocationManager`
        - splitting the file is still correct, but moving the runtime class should wait until the `LocationManager` boundary is cleaner
      - `MapScreenScaffoldInputModel.kt` and `MapScreenScaffoldInputs.kt` still carry concrete `LocationManager`, `MapCameraManager`, and `MapLifecycleManager` types through a broad shell signature
        - introducing a non-Compose runtime manager aggregate only helps if the shell wrapper continues to own those concrete fields and unwrap them locally
        - otherwise the aggregate becomes an extra layer without reducing shell signature churn
      - `MapScreenRoot.kt`, `MapOverlayStack.kt`, `MapScreenSections.kt`, `MapGestureSetup.kt`, `MapScreenRuntimeEffects.kt`, and `MapComposeEffects.kt` all still depend directly on the concrete manager classes
        - that is acceptable for a shell depending on runtime types
        - but it means the immediate compile-speed gain from moving a single manager class will still be limited unless a larger runtime cluster moves behind the same boundary
    - 7B.1 is therefore refined again:
      - extract top-level `DisplayPoseSnapshot`
      - split `MapLifecycleManager.kt` without moving the runtime class yet
      - remove `MapCameraManager.mapState` leakage by replacing direct shell access with a narrower API
      - add a non-Compose runtime-manager builder in `:feature:map-runtime`, but keep the shell `MapScreenManagers` data class and wrapper local
      - only after those prep changes, reassess whether `MapCameraManager` is the first worthwhile runtime-class move
    - Implemented 2026-03-12 prep slice:
      - moved `DisplayPoseSnapshot` to `feature/map-runtime` as a top-level runtime contract
      - split `MapLifecycleEffects` into its own shell file and left the runtime `MapLifecycleManager` class in place
      - removed the direct `MapCameraManager.mapState` shell leak by adding a narrow map accessor used by `MapCameraEffects`
      - deliberately deferred the non-Compose runtime-manager builder because this slice did not yet reduce shell signature width enough to justify another abstraction
      - `LocationManager` and `SnailTrailManager` remain deferred
    - 2026-03-12 focused 7B.2 repass: a non-Compose runtime-manager builder/factory is not yet justified as a no-churn phase
      - `MapScreenManagers` is still a shell-local aggregate used directly by `MapScreenRoot` and `rememberMapScreenScaffoldInputs`; introducing a runtime builder would not remove the shell aggregate by itself
      - the shell still fans concrete manager types out through the live render chain:
        - `MapScreenScaffoldInputs`
        - `MapScreenScaffoldContentHost`
        - `MapScreenContent`
        - `MapOverlayStack`
        - `MapScreenRootEffects`
      - that means a builder would add a construction layer, but would not materially reduce shell signature width or the number of concrete runtime types crossing the shell boundary
      - `MapScreenScaffoldInputModel.kt` is currently not an active seam and should not drive `7B.2`; the live shell path is `MapScreenScaffoldInputs`
      - `MapInitializer` is still a mixed shell/runtime collaborator because it depends on:
        - `Context`
        - `MapScreenState`
        - `MapOverlayManager`
        - `OrientationManager`
        - `SnailTrailManager`
        - `TaskRenderSyncCoordinator`
        - shell-owned lifecycle/MapView readiness flow
        so it is not a safe first payload for a builder phase
      - `MapRuntimeController` is already a shell-local imperative bridge around `MapOverlayManager`; a builder would not replace or simplify that ownership
      - conclusion:
        - do not implement a standalone builder/factory phase yet
        - only revisit `7B.2` if a future prep slice measurably reduces shell signature width or isolates a real runtime manager cluster behind a smaller facade
        - otherwise stop at the current `7B.1` boundary and avoid speculative churn
      - strongest next real runtime-cluster candidate if Phase 7 continues:
        - follow the dedicated [Map_Overlay_Runtime_Core_Extraction_Plan_2026-03-12.md](/C:/Users/Asus/AndroidStudioProjects/XCPro/docs/refactor/Map_Overlay_Runtime_Core_Extraction_Plan_2026-03-12.md) instead of doing more generic Phase 7 micro-slices
        - exact seam after the Phase C dependency pass:
          - forecast/weather runtime leaf is already in `:feature:map-runtime`
          - traffic/OGN runtime delegates are already leaf-owned in `:feature:traffic`
          - the next real move is the remaining `MapOverlayManagerRuntime` owner, but only after C1 removes its direct `MapScreenState` ownership and internal shell-collaborator construction
          - after the latest C2 seam pass, this is now primarily a file-owner move:
            - `MapOverlayManagerRuntime.kt`
            - `MapOverlayRuntimeInteractionDelegate.kt`
          - `MapOverlayRuntimeInteractionDelegateTest.kt` must move with the helper because the delegate is `internal`
          - the existing `MapOverlayManager*` behavior tests remain shell-owned because they instantiate `MapOverlayManager`, not `MapOverlayManagerRuntime`
        - keep shell-owned for now:
          - `MapOverlayManagerRuntimeBaseOpsDelegate.kt`
          - `MapOverlayRuntimeStateAdapter.kt`
          - `MapOverlayRuntimeMapLifecycleDelegate.kt`
          - `MapOverlayRuntimeStatusCoordinator.kt`
          - `MapOverlayManagerRuntimeStatus.kt`
        - keep as shell-owned bridges:
          - `MapOverlayManager.kt` as the thin shell-facing adapter
          - `MapRuntimeController.kt`
          - `MapTrafficOverlayUiAdapters.kt`
          - `MapOverlayStack.kt`
          - `MapScreenRoot.kt` / `MapScreenScaffold*`
        - rationale:
          - this cluster is still the biggest remaining structural compile-speed win
          - but the exact Phase C pass showed that the remaining owner move has to be split into:
            - shell/runtime boundary narrowing around `MapOverlayManagerRuntime`, with `MapOverlayManager.kt` absorbing shell collaborator construction
            - then the actual runtime-owner move
    - do not move `MapScreenManagers.kt` wholesale:
      - keep the Compose `rememberMapScreenManagers(...)` wrapper and the current shell aggregate in `feature:map`
      - extract a narrower non-Compose runtime factory / runtime aggregate first, then have the shell wrapper compose over that runtime aggregate plus shell-only managers
    - keep shell-only manager ownership in `feature:map`:
      - `MapUIWidgetManager`
      - `MapModalManager`
      - `MapOverlayStack.kt`
      - `MapTrafficOverlayUiAdapters.kt`
    - split `MapLifecycleManager.kt` before moving it:
      - move the `MapLifecycleManager` runtime class with the runtime module
      - keep `MapLifecycleEffects` in `feature:map/ui` as the Compose lifecycle bridge
    - re-home shared runtime inputs before moving manager implementations:
      - `MapFeatureFlags` cannot remain shell-owned because it is already consumed by runtime classes such as `LocationManager`, `SnailTrailManager`, `DisplayPoseRenderCoordinator`, `DisplayPoseFrameLogger`, `MapTrackingCameraController`, and replay/runtime coordinators
      - `MapLocationUiModel` cannot remain shell-owned because both the runtime cluster (`LocationManager`, `MapOverlayManagerRuntime`) and shell UI consume it
      - these types need either a `:feature:map-runtime` home or a dedicated shared runtime-contract/model home before the manager move
    - move the runtime trail cluster with the first runtime-core extraction:
      - `trail/SnailTrailManager.kt`
      - `trail/SnailTrailOverlay.kt`
      - any direct trail runtime support classes still required by moved managers
    - `TaskRenderSyncCoordinator.kt` is now in `:feature:map-runtime`, but only behind an explicit injected render-router seam from `feature:map`
    - keep `MapTaskScreenManager.kt` and `MapRuntimeController.kt` deferred until after the initial runtime factory split is stable
  - Phase 7C: runtime facade narrowing
    - add a narrower runtime facade for shell entrypoints before attempting any move of `MapScreenContentRuntime*` or `MapRuntimeController.kt`
    - do not carry the current long concrete runtime parameter lists across the module boundary unchanged
  - Phase 7D: deferred compatibility cleanup
    - only after the shell/runtime split is stable, reassess whether `MapBottomSheetTabs.kt`, `MapTaskScreenUi.kt`, `Task.kt`, `MapTaskScreenManager.kt`, or `MapScreenContentRuntime*` still need local follow-up reductions
- Explicitly keep unchanged in Phase 7:
  - route names and public entrypoints
  - `AppNavGraph.kt`
  - extracted owner-module boundaries for `feature:forecast`, `feature:weather`, `feature:tasks`, and `feature:weglide`
  - `MapScreenViewModel.kt` line-budget cleanup; that is a separate repo-health concern, not part of the compile-speed phase
  - existing `com.example.xcpro.map*` package names, unless a later cleanup proves a rename is necessary
- Tests to add/update:
  - compile verification for both modules:
    - `./gradlew :feature:map-runtime:compileDebugKotlin`
    - `./gradlew :feature:map:compileDebugKotlin`
  - move runtime-owned unit tests with the moved manager/runtime files
  - keep route/shell tests in `feature:map`
  - rerun the full required repo gates after each non-trivial sub-slice
- Exit criteria:
  - small shell edits in `feature:map` no longer compile the heavy MapLibre/runtime implementation files
  - heavy runtime ownership lives in `:feature:map-runtime`
  - `feature:map` remains the stable route/shell module and depends on runtime in only one direction
  - no duplicate secret/config ownership is introduced during the split
  - any remaining shell follow-up after the split is justified by fresh measurements, not by guesswork

## 4A) Anti-Churn Execution Rules

These rules apply to every remaining phase from this point forward. They exist
to prevent ad hoc refactors that add surface area without materially improving
 incremental compile performance.

- Only implement a new phase when the seam is proven by at least one of:
  - a measured compile hotspot
  - a shell/runtime back-edge or dependency-direction blocker
  - a contract leak that prevents a planned module move
  - a required architecture-rule fix
- Do not implement a phase based only on “cleanup”, “consistency”, or “it might
  be cleaner”.
- Before moving any file or class, explicitly confirm:
  - no reverse dependency will be introduced
  - shell-only types are not leaked in the moved public surface
  - mixed shell/runtime files are split first instead of moved wholesale
- Prefer prep slices over moves:
  - extract top-level contract/model types
  - split mixed shell/runtime files
  - narrow leaked APIs
  - only then move runtime-owned files
- Do not add a new abstraction unless it removes an existing boundary problem.
  - Example: a runtime builder/factory is justified only if it meaningfully
    reduces shell wiring churn or enables a clean runtime move.
  - If it adds a layer without reducing shell signature width, defer it.
- Keep package names stable unless a package move is required to preserve
  dependency direction.
- Update only active documentation for each phase:
  - `docs/ARCHITECTURE/PIPELINE.md`
  - this plan document
  - leave historical reports and older dated notes unchanged
- Each phase must declare its entry criteria before code changes:
  - exact files in scope
  - exact files explicitly out of scope
  - expected compile-speed outcome
  - verification and measurement commands
- Each phase must stop and remeasure before the next phase begins.
  - Do not queue speculative follow-up phases without a fresh hotspot pass.
- Treat unrelated red repo items as separate tracks and do not mix them into
  compile-speed work:
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
    line-budget gate
  - `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryTest.kt`
- Stop rule:
  - if the next candidate phase does not promise a broad module-boundary win or
    remove a concrete runtime-boundary blocker, stop structural refactoring and
    return to feature work.


## 5) Test Plan

- Unit tests:
  - move and preserve existing slice-local tests with each extraction
- Replay/regression tests:
  - rerun replay and IGC tests whenever replay/IGC ownership moves
- UI/instrumentation tests:
  - run targeted tests for forecast/weather/task surfaces when their module moves affect UI wiring
- Degraded/failure-mode tests:
  - Hilt wiring failures
  - missing bindings after module extraction
  - retained map shell integration after leaf module moves
- Boundary tests for removed bypasses:
  - map shell still consumes extracted slice APIs without direct owner leakage

Build-speed measurement commands:

```bash
./gradlew :feature:map:assembleDebug
./gradlew :feature:map:compileDebugKotlin
./gradlew :feature:map:assembleDebug
```

For extracted leaf modules, use the equivalent:

```bash
./gradlew :feature:<new-module>:compileDebugKotlin
./gradlew :feature:<new-module>:assembleDebug
```

Required checks after each non-trivial implementation phase:

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
| Split picks a highly coupled seam and yields churn without speed gain | High | Start with `forecast`, then rain weather, then the bigger task extraction | Codex / repo owner |
| `feature:map` is renamed or broken apart too aggressively | High | Keep `feature:map` as the compatibility shell throughout the plan | Codex / repo owner |
| Hilt graph breaks during module moves | High | Move DI files with the owner slice and verify compile/assemble immediately | Codex / repo owner |
| Wind/replay coupling is broken by moving all weather code at once | High | Move rain first; keep `weather/wind` with sensors/replay until explicitly planned | Codex / repo owner |
| Task extraction destabilizes map/task behavior | High | Treat task extraction as its own major phase with targeted regression tests | Codex / repo owner |
| Shell/runtime split introduces a reverse dependency between `feature:map` and `feature:map-runtime` | High | Keep `feature:map` as the shell and require all moved runtime code to depend only outward on leaf modules and core modules, never back on the shell | Codex / repo owner |
| Secrets/config are duplicated during the runtime split | Medium | Treat `OPENSKY_*` ownership as a separate concern; do not copy current `feature:map` `BuildConfig` fields into the runtime module unless a moved runtime file proves it needs them | Codex / repo owner |
| Claimed speedups are noise or cache artifacts | Medium | Use the same warm-command measurement pattern before and after each phase | Codex / repo owner |
| Windows KSP file-lock failures pollute measurements | Medium | Avoid overlapping Gradle builds during measurement and implementation | Codex / repo owner |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` or `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- No feature dependency cycles introduced
- `feature:map` remains the compatibility shell until extractions are complete
- `feature:map` no longer owns extracted leaf slices once their phases complete
- Compile speed improves against the declared build-performance contract
- Full required repo gates continue to pass:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## 8) Rollback Plan

- What can be reverted independently:
  - each leaf extraction phase can be reverted independently as long as the old package ownership remains recoverable
  - processor removal in Phase 1 can be reverted independently from module moves
- Recovery steps if regression is detected:
  - revert the most recent extraction phase only
  - keep `feature:map` as the fallback owner until the phase is reworked
  - restore the moved DI bindings with the reverted slice
  - rerun the compile sample and full gates before retrying

## 9) Recommendation

Implement this plan in this order:

1. Phase 1 compile hygiene
2. Phase 2A `feature:forecast` bootstrap
3. Phase 2B map-owned forecast implementation extraction with `feature:map` route/runtime shells retained
4. Phase 3 rain-weather extraction
5. Phase 4 IGC ownership cleanup
6. Phase 5 task extraction
7. Phase 5C task panel helper extraction
8. Phase 6 residual shell reduction through 6D
9. Phase 7 `feature:map` shell/runtime split

That ordering gives:

- immediate low-risk compile cleanup
- no-churn ownership cleanup before moving runtime-bearing code
- fast proof that the forecast slice can be extracted cleanly
- no ad hoc scripts
- no big-bang churn
- a narrow task-panel helper extraction before attempting to reduce the remaining mixed `feature:map` wrapper shells
- a measurement-driven stop point after the low-churn shell reductions
- the remaining broad compile-speed win only after splitting the retained shell from the heavy map runtime instead of chasing one more tiny UI seam
