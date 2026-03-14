# Map Screen Shell Close-Out Review

## 0) Metadata

- Title: Close-out review for MapScreen shell ownership extraction
- Owner: Codex
- Date: 2026-03-14
- Parent plan:
  - `docs/refactor/Map_Screen_Shell_Ownership_Extraction_Plan_2026-03-14.md`
- Decision:
  - close item 1 at the ownership/refactor level
  - do not start optional Phase 4B
  - carry only the remaining evidence follow-up if a fresh MapScreen SLO package is required for merge/release

## 1) Review Summary

The original goal for item 1 was to narrow the MapScreen shell without ad hoc churn:

- split shell-only UI state out of the monolithic content runtime
- replace broad scaffold/content contracts with grouped inputs
- reduce `MapScreenRoot.kt` to an assembler
- narrow `MapScreenViewModel.kt` only after the shell seam was proven

That goal is now met.

The refactor stayed on the intended seam:

- Phase 1 moved QNH, forecast/weather, bottom-tab, and wind-tap ownership out of `MapScreenContentRuntime.kt`
- Phase 2 narrowed scaffold/content handoff into grouped `scaffold` and `content` inputs
- Phase 3 grouped bindings and root UI collection so `MapScreenRoot.kt` stopped carrying mixed fanout
- Phase 4A extracted the proven profile/session and WeGlide prompt seams from `MapScreenViewModel.kt`

## 2) Evidence

### 2.1 Ownership Outcome

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
  - reduced from the earlier `476` line hotspot to `380` lines
  - now assembles focused UI state helpers instead of directly owning all shell-local state
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenQnhUiState.kt`
  - owns QNH dialog UI state and input adaptation
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenForecastWeatherState.kt`
  - owns forecast/weather collection and shell adaptation
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBottomTabsUiState.kt`
  - owns bottom-tab and sheet-local UI state
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenWindTapUiState.kt`
  - owns wind-tap geometry/display state
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSectionInputs.kt`
  - owns grouped content-section contracts
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindingGroups.kt`
  - owns grouped map/session/task/traffic binding collection
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenProfileSessionDependencies.kt`
  - owns the profile/session dependency group for the proven ViewModel seam
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenProfileSessionCoordinator.kt`
  - owns profile/style/layout routing
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenWeGlidePromptBridge.kt`
  - owns prompt collection and confirm/dismiss resolution
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - reduced from the `392` line Phase 4 seam-lock baseline to `330` lines
  - remains the single screen-state owner instead of turning into a facade over hidden state owners

### 2.2 Verification Outcome

Passed during Phase 4A completion:

- `./gradlew enforceRules --no-configuration-cache`
- `./gradlew testDebugUnitTest --no-configuration-cache`
- `./gradlew assembleDebug --no-configuration-cache`
- `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapScreenWeGlidePromptBridgeTest" --tests "com.example.xcpro.map.MapScreenViewModelCoreStateTest" --no-configuration-cache`

Manual validation completed after the implementation:

- map entry and map render
- drawer/back handling
- task panel open/close
- bottom tabs and sheet flow
- QNH dialog invalid/valid path
- traffic/weather overlay path
- replay/prompt routing

## 3) Acceptance Review

Satisfied:

- no new SSOT duplication introduced in the shell path
- `MapScreenContentRuntime.kt` is no longer the mixed owner it was at the start
- scaffold/content contracts are grouped by concern
- `MapScreenRoot.kt` is now assembler-oriented
- `MapScreenViewModel.kt` remains the screen owner while staying under the enforced hotspot budget
- required Gradle gates passed on the implemented slice

Still open:

- this close-out does not include a refreshed `artifacts/mapscreen/...` evidence package for impacted `MS-UX-*` / `MS-ENG-*` gates
- if strict MapScreen visual gate evidence is required for merge/release, that package still needs to be attached or refreshed

Review conclusion:

- item 1 is complete as an ownership refactor
- item 1 is not a reason to keep refactoring the map shell
- optional Phase 4B is not justified now

## 4) Quality Rescore

- Architecture cleanliness: `4.7 / 5`
  - Evidence: shell state owners are split, grouped contracts are explicit, `MapScreenViewModel.kt` no longer carries the old profile/prompt block inline
  - Remaining risk: map visual evidence package is not refreshed in this close-out doc
- Maintainability / change safety: `4.6 / 5`
  - Evidence: root, scaffold, content, and ViewModel seams are all narrower; hotspot files are smaller and more reviewable
  - Remaining risk: forecast/weather runtime itself is still a separate hotspot outside this shell slice
- Test confidence on risky paths: `4.2 / 5`
  - Evidence: Gradle gates passed, new prompt bridge and bottom-tab state tests exist, focused smoke passed
  - Remaining risk: shell-specific manual smoke is stronger than the automated regression depth on every map interaction path
- Overall map/task slice quality: `4.6 / 5`
  - Evidence: main-screen ownership is materially improved without reopening unrelated task/runtime work
  - Remaining risk: weather/runtime and some separate map-runtime hotspots still exist outside this slice
- Release readiness (map/task slice): `4.1 / 5`
  - Evidence: compile, unit-test, assemble, and focused smoke are green
  - Remaining risk: attach/refresh impacted MapScreen SLO evidence before calling the slice fully release-closed under the strict map gate

## 5) Next Move

The correct next ownership target is the profile/card boundary.

Do not continue refactoring the map shell.
Do not start forecast/weather runtime before the profile/card seam is locked.
