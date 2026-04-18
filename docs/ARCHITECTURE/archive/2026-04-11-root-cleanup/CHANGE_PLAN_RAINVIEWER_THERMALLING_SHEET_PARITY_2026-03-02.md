# CHANGE_PLAN_RAINVIEWER_THERMALLING_SHEET_PARITY_2026-03-02.md

## 0) Metadata

- Title: RainViewer settings host parity with Thermalling bottom-sheet behavior
- Owner: XCPro map/ui
- Date: 2026-03-02
- Issue/PR: TBD
- Status: Complete

## 1) Scope

- Problem statement:
  - RainViewer settings currently open from General using route navigation (`SettingsRoutes.WEATHER_SETTINGS`) to a screen hosted by `Scaffold`.
  - Thermalling settings are already hosted as full-height `ModalBottomSheet` with one-swipe dismiss (`skipPartiallyExpanded = true`), including General-local sheet usage.
  - This creates interaction and visual-host inconsistency between RainViewer and Thermalling.
- Why now:
  - User request is explicit: RainViewer should match Thermalling bottom-sheet behavior.
  - Host inconsistency increases UX drift and test surface complexity.
- In scope:
  - Convert RainViewer settings host behavior to match Thermalling sheet behavior.
  - Keep weather domain/use-case/repository logic unchanged.
  - Keep weather map overlay runtime wiring unchanged.
  - Define compatibility strategy for the map Weather tab "More settings" entrypoint.
  - Add regression coverage for sheet host behavior and close semantics.
- Out of scope:
  - Weather overlay algorithm/policy changes.
  - Rain metadata/network pipeline changes.
  - Changes to OGN/Hotspots/Thermalling settings content semantics.
- User-visible impact:
  - RainViewer opens with full-height sheet behavior consistent with Thermalling.
  - One swipe down closes RainViewer sheet.
  - Top app bar actions and dismiss vectors are deterministic.

## 2) Investigation Findings (Comprehensive Code Pass)

1) General RainViewer tile currently uses route navigation, not General-local sheet state.
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt:305-309`

2) RainViewer route host is currently full-screen `Scaffold`, not `ModalBottomSheet`.
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt:117`

3) Thermalling route host uses production sheet pattern already.
- `rememberModalBottomSheetState(skipPartiallyExpanded = true)`
- full-height container + weighted content body
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ThermallingSettingsScreen.kt:70-123`

4) Thermalling is already General-local sub-sheet capable.
- `GeneralSubSheet.THERMALLING` local state owner
- `ThermallingSettingsSubSheet` full-height `ModalBottomSheet`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt:397-401`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt:630-677`

5) Map Weather tab "More settings" currently navigates directly to `SettingsRoutes.WEATHER_SETTINGS`.
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt:134-143`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabs.kt:160-166`

6) Existing weather tests focus on policy/state wiring, not sheet host parity semantics.
- `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreenPolicyTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsViewModelTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsUseCaseTest.kt`

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Rain overlay preferences (`enabled`, `opacity`, animation/window/speed/transition/frame mode/render opts) | `WeatherOverlayPreferencesRepository` | `Flow<WeatherOverlayPreferences>` via use-case/viewmodel | UI-local authoritative copies outside VM |
| Rain overlay runtime state (selected frame/status/ages) | `ObserveWeatherOverlayStateUseCase` + `WeatherOverlayViewModel` | `StateFlow<WeatherOverlayRuntimeState>` | alternate UI-owned runtime derivation |
| RainViewer sheet visibility (route-host) | Nav back stack (`SettingsRoutes.WEATHER_SETTINGS`) | route presence | extra global show/hide flags |
| RainViewer General-local sub-sheet visibility (if enabled in Phase 3) | General screen local sub-sheet state | local enum state | duplicate booleans per sheet owner |

### 3.2 Dependency Direction

Remains:

`UI -> domain -> data`

- Planned files:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt`
  - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt` (compatibility checks only if needed)
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt` (entrypoint hardening if needed)
  - tests under `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer`
- Boundary risk:
  - Host migration must not move weather business logic into UI host code.

### 3.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| RainViewer route host container | `Scaffold` host in `WeatherSettingsScreen` | Thermalling-style full-height `ModalBottomSheet` host | behavioral parity + single-swipe close | compose tests + manual matrix |
| RainViewer content ownership | mixed host/content in one composable | extracted host-agnostic `WeatherSettingsContent` | reuse across route-host and General-local host | compile + policy tests |
| General RainViewer tile behavior (optional but recommended in this plan) | direct route navigate | General local sub-sheet state (`GeneralSubSheet.WEATHER`) | same behavior family as Thermalling in General | sub-sheet transition tests |

### 3.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| General RainViewer tile | direct `navController.navigate(SettingsRoutes.WEATHER_SETTINGS)` | `activeSubSheet = GeneralSubSheet.WEATHER` | Phase 3 |
| RainViewer host reuse | route-only weather host | shared `WeatherSettingsContent` used by route sheet + General sheet | Phase 1-2 |

### 3.3 Time Base

No domain timebase changes.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| RainViewer sheet open/close state | N/A | UI navigation state only |
| Existing weather freshness/age labels | unchanged (wall-derived in existing weather path) | no policy change in this plan |

Explicitly forbidden:
- introducing new time math in UI host code.
- changing weather freshness policy as part of host migration.

### 3.4 Threading and Cadence

- UI host events remain on `Main`.
- No new background cadence/work introduced.
- Weather runtime cadence remains owned by existing weather use-case/repository pipeline.

### 3.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence: none introduced (weather settings host only).

### 3.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| UI host drift causes inconsistent close behavior | ARCHITECTURE UDF/predictability | compose tests + manual matrix | new `WeatherSettingsSheetBehaviorTest` |
| Duplicate sheet state owners | ARCHITECTURE SSOT | review + tests | `GeneralSettingsScreenPolicyTest` updates |
| Business logic leakage into host composable | ARCHITECTURE boundaries | code review + existing weather tests | weather VM/use-case tests remain green |
| Route regression for map weather-tab entrypoint | navigation contract | unit/compose + manual checks | `MapScreenScaffold` path validation |

## 4) Data Flow (Before -> After)

Before:

`General tile -> navigate(WEATHER_SETTINGS) -> WeatherSettingsScreen (Scaffold host)`

`Map Weather tab -> navigate(WEATHER_SETTINGS) -> WeatherSettingsScreen (Scaffold host)`

After:

`General tile -> General local sheet state (WEATHER) -> WeatherSettingsContent in Thermalling-style ModalBottomSheet`

`Map Weather tab -> navigate(WEATHER_SETTINGS) -> WeatherSettingsScreen as Thermalling-style ModalBottomSheet using shared WeatherSettingsContent`

## 5) Production-Grade Phased Implementation

### Phase 0 - Baseline Lock and Behavior Matrix

- Goal:
  - Lock current behavior and target parity contract before edits.
- Deliverables:
  - open/dismiss matrix for:
    - General -> RainViewer
    - Map Weather tab -> More settings -> RainViewer
    - dismiss by swipe, top-left back icon, top-right map icon, system back
- Files:
  - this plan doc.
- Exit criteria:
  - target parity matrix is explicit and approved.

### Phase 1 - Extract Host-Agnostic RainViewer Content

- Goal:
  - Separate weather content from host container to allow sheet-host reuse.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
- Required implementation quality:
  - Introduce `WeatherSettingsContent(...)` that contains existing cards/controls only.
  - Preserve all existing viewmodel bindings and weather control semantics.
  - Keep existing policy helper functions (`isFrameSourceControlEnabled`, etc.) stable.
- Tests:
  - keep `WeatherSettingsScreenPolicyTest` passing.
- Exit criteria:
  - behavior unchanged; content reusable by multiple hosts.

### Phase 2 - Convert Route Weather Host to Thermalling-Style Sheet

- Goal:
  - Make `SettingsRoutes.WEATHER_SETTINGS` host match Thermalling sheet contract.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
- Required implementation quality:
  - Replace root `Scaffold` host with `ModalBottomSheet`.
  - Use `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
  - Use full-height container and weighted content body (Thermalling pattern).
  - Set `dragHandle = null` for parity.
  - Keep top app bar actions deterministic (`navigateUp` / drawer / map).
- Tests:
  - add/extend weather sheet host behavior tests (close vectors and full-height contract where feasible).
- Exit criteria:
  - RainViewer route opens full-height sheet and closes in one swipe.

### Phase 3 - General RainViewer Local Sheet Parity (Recommended)

- Goal:
  - Align General tile interaction with Thermalling local sheet model.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt`
- Required implementation quality:
  - Add `GeneralSubSheet.WEATHER`.
  - Change RainViewer tile click from route navigation to local sub-sheet state transition.
  - Add `WeatherSettingsSubSheet` using shared `WeatherSettingsContent`.
  - Keep single active sub-sheet invariant.
- Tests:
  - update `GeneralSettingsScreenPolicyTest` for RainViewer transition ownership.
  - add/extend sub-sheet transition tests (`NONE -> WEATHER`, dismiss -> `NONE`).
- Exit criteria:
  - General RainViewer behavior matches Thermalling General behavior.

### Phase 4 - Entrypoint Compatibility and Navigation Hardening

- Goal:
  - Preserve map Weather tab behavior while converging to sheet parity.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt`
  - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt` (if compatibility wrappers needed)
- Required implementation quality:
  - Keep `SettingsRoutes.WEATHER_SETTINGS` route functional for map-tab entry.
  - Add `launchSingleTop` if repeated navigation duplication is observed.
  - Ensure no duplicate sheet stacks on rapid taps.
- Tests:
  - navigation idempotency test where feasible.
  - manual rapid-tap matrix.
- Exit criteria:
  - map-tab entry remains stable with no duplicate route stacking.

### Phase 5 - Verification and Evidence

- Required checks:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Optional when device/emulator available:
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
  - `./gradlew connectedDebugAndroidTest --no-parallel`
- Docs sync:
  - `PIPELINE.md`: no update expected unless flow description becomes inaccurate.
  - `KNOWN_DEVIATIONS.md`: update only if approved exception is required.
- Exit criteria:
  - all required checks pass, behavior matrix is green, and no architecture drift is introduced.

## 6) Test Plan (Consolidated)

- Unit/Compose:
  - keep existing:
    - `WeatherSettingsScreenPolicyTest`
    - `WeatherSettingsUseCaseTest`
    - `WeatherSettingsViewModelTest`
  - add/update:
    - `GeneralSettingsScreenPolicyTest` (RainViewer ownership/route parity assertions)
    - new `WeatherSettingsSheetBehaviorTest` (route sheet close semantics)
    - optional `GeneralSettingsSubSheetBehaviorTest` for `WEATHER` transitions
- Manual matrix:
  - General -> RainViewer -> swipe down closes in one swipe
  - General -> RainViewer -> back/map icon close behavior
  - Map Weather tab -> More settings -> RainViewer sheet behavior
  - Rapid re-open does not duplicate stack/sheets

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Host migration accidentally changes weather control behavior | Medium | extract content first; preserve callbacks and helper policy functions | XCPro map/ui |
| Dual entrypoints (General + map tab) drift in behavior | Medium | shared `WeatherSettingsContent`; one sheet host pattern | XCPro map/ui |
| Nested sheet interactions regress dismiss behavior | High | explicit close matrix + compose/manual tests | XCPro map/ui |
| Navigation duplication on rapid actions | Medium | route guard + optional `launchSingleTop` hardening | XCPro map/ui |

## 8) Acceptance Gates

- RainViewer route host matches Thermalling bottom-sheet contract:
  - full-height sheet
  - one-swipe close (`skipPartiallyExpanded = true`)
- General RainViewer behavior is sheet-based and consistent with Thermalling (if Phase 3 applied).
- Weather settings content and behavior remains functionally unchanged.
- No architecture violations (`ARCHITECTURE.md`, `CODING_RULES.md`).
- No new SSOT duplication.
- Required verification commands pass.

## 9) Rollback Plan

- Independent rollback units:
  - revert Phase 3 (General local RainViewer sheet) while keeping route sheet migration.
  - revert Phase 2 host migration and keep extracted content (Phase 1) for low-risk retry.
- Recovery steps:
  1. restore route-host `Scaffold` if sheet regressions are found.
  2. restore General RainViewer tile route navigation.
  3. keep shared content extraction if stable to reduce future migration risk.
  4. rerun required verification commands.

## 10) Quality Rescore Requirement (Post-Implementation)

After implementation, rescore using `docs/ARCHITECTURE/AGENT.md`:

- Architecture cleanliness
- Maintainability/change safety
- Test confidence on risky paths
- Overall map/task slice quality
- Release readiness

Scores must be evidence-based with changed files and test references.

## 11) Implementation Closure (2026-03-02)

Implemented production behavior:

- Route-host RainViewer (`SettingsRoutes.WEATHER_SETTINGS`) now uses Thermalling-style
  full-height `ModalBottomSheet` with one-swipe close (`skipPartiallyExpanded = true`, `dragHandle = null`).
- General tile RainViewer now opens a General-local sub-sheet (`GeneralSubSheet.WEATHER`)
  with the same full-height sheet behavior.
- Weather settings host chrome is now reusable as `WeatherSettingsSheet(...)`
  and shared between route-host and General-local hosts.
- Weather settings UI content remains reusable via `WeatherSettingsContent(...)`.
- Map weather-tab route open hardening applied:
  - guarded route check via `shouldNavigateToWeatherSettings(...)`
  - navigation uses `launchSingleTop = true` to reduce duplicate stacking on rapid taps.
- Added instrumentation-level sheet close semantics tests for RainViewer host:
  - swipe down closes in one gesture
  - back action dismisses deterministically

Files changed:

- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabs.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/ui/MapScreenScaffoldPolicyTest.kt`
- `feature/map/src/androidTest/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsSheetBehaviorInstrumentedTest.kt`

Verification evidence:

- `./gradlew enforceRules --no-daemon --no-configuration-cache` -> PASS
- `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache` -> PASS
- `./gradlew assembleDebug --no-daemon --no-configuration-cache` -> PASS
- Focused safety checks:
  - `./gradlew :feature:map:compileDebugKotlin --no-daemon --no-configuration-cache` -> PASS
  - `./gradlew :feature:map:compileDebugAndroidTestKotlin --no-daemon --no-configuration-cache` -> PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.ui.MapScreenScaffoldPolicyTest" --tests "com.trust3.xcpro.screens.navdrawer.GeneralSettingsScreenPolicyTest" --tests "com.trust3.xcpro.screens.navdrawer.WeatherSettingsScreenPolicyTest" --no-daemon --no-configuration-cache` -> PASS
  - `./gradlew :feature:map:clean --no-daemon --no-configuration-cache` -> PASS (performed once to recover transient KSP file-lock/cache issue)
- Known transient in verification:
  - `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache` showed one intermittent unrelated failure in `app` module (`ProfileRepositoryTest.ioReadError_preservesLastKnownGoodState`) on first run, then passed on immediate rerun.
- Instrumentation execution:
  - Attempted:
    - `./gradlew :feature:map:connectedDebugAndroidTest --no-daemon --no-configuration-cache "-Pandroid.testInstrumentationRunnerArguments.class=com.trust3.xcpro.screens.navdrawer.WeatherSettingsSheetBehaviorInstrumentedTest"` -> FAIL (device-side compose-host issue).
    - `./gradlew :feature:map:connectedDebugAndroidTest --no-daemon --no-configuration-cache "-Pandroid.testInstrumentationRunnerArguments.class=com.trust3.xcpro.map.ui.AdsbStatusBadgesInstrumentedTest"` -> FAIL with same error.
  - Failure signature on connected device:
    - `IllegalStateException: No compose hierarchies found in the app` (affects pre-existing instrumentation class too, so this is treated as environment/baseline and not a regression from this change set).

Quality rescore (evidence-based):

- Architecture cleanliness: 5.0 / 5
  - No domain/data boundary drift; changes isolated to UI host/navigation logic.
- Maintainability / change safety: 4.9 / 5
  - Shared `WeatherSettingsSheet` host reduces chrome drift between entry points.
- Test confidence on risky paths: 4.8 / 5
  - Added route guard policy test plus instrumented sheet close semantics coverage.
- Overall map/task slice quality: 4.8 / 5
  - Behavior parity, deterministic dismiss vectors, and navigation hardening are now implemented together.
- Release readiness (map/task slice): 4.9 / 5
  - Required gates green on rerun; remaining risk is flaky unrelated app test and optional connected-test coverage.
