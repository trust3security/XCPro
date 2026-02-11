# ADS-b Nav Drawer Settings Change Plan

## 0) Metadata

- Title: ADS-b General Settings Page + Map Icon Size Slider
- Owner: XCPro Team
- Date: 2026-02-10
- Issue/PR: TBD
- Status: Draft

## 1) Fixed Compliance Matrix (Locked Before Coding)

| ID | Requirement | Evidence Location | Verification Method |
|---|---|---|---|
| CM-01 | New `ADS-b` entry exists under `General` and opens a dedicated screen | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt`, `app/src/main/java/com/example/xcpro/AppNavGraph.kt` | Manual nav check + route review |
| CM-02 | New `ADS-b` screen uses `SettingsTopAppBar` pattern (left arrows + map icon + same color scheme) like `Layouts` | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt` | UI review against `Layout.kt` |
| CM-03 | Slider range is exactly `50..124` px and default is `56` px | `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt`, `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt` | Unit tests + manual screen check |
| CM-04 | ADS-b icon size is persisted (DataStore SSOT) and restored after restart | `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt` | Repository test + relaunch manual check |
| CM-05 | Flow remains layer-correct (`UI -> ViewModel -> UseCase -> PreferencesRepository`) | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsViewModel.kt`, `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt` | Code audit (imports/dependencies) |
| CM-06 | MapLibre ADS-b icons resize live when setting changes (without requiring app restart) | `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`, `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt` | Manual runtime check |
| CM-07 | Icon size applies to newly recreated overlays (style changes, map re-init) | `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt` | Manual style-switch check |
| CM-08 | Tap-selection on ADS-b symbols still works after resize | `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt` | Manual tap test at min/max size |
| CM-09 | Regression tests cover clamp/default + VM wiring | `feature/map/src/test/java/com/example/xcpro/adsb/`, `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt` | Test run output |
| CM-10 | Required repo checks pass | Gradle tasks | `./gradlew enforceRules`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` |
| CM-11 | Pipeline docs updated for new ADS-b settings-to-overlay wiring | `docs/ARCHITECTURE/PIPELINE.md` | Doc diff review |
| CM-12 | No untracked architecture deviation is introduced | `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` | Must remain unchanged unless exception approved |

## 2) Scope

- Problem statement:
  - ADS-b icon size is fixed in map rendering (`ICON_BITMAP_SIZE_PX = 56`) and cannot be adjusted by users.
  - General settings has no dedicated ADS-b settings page.
- Why now:
  - Users requested explicit ADS-b settings with icon size control and the same top bar UX pattern used by `Layouts`.
- In scope:
  - Add new `ADS-b` page entry in `General`.
  - Add ADS-b settings screen with first slider controlling map icon size (`50..124`, default `56`).
  - Persist size in ADS-b preferences.
  - Wire size into map overlay runtime updates and overlay recreation paths.
- Out of scope:
  - ADS-b data source/network logic changes.
  - ADS-b metadata/details sheet feature changes.
  - OGN icon sizing.
- User-visible impact:
  - Users can open `General -> ADS-b` and tune ADS-b icon size on map.

## 3) Current-State Findings (Investigation Baseline)

- General screen and `Layouts` entry exist:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt:71`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt:222`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt:224`
- Shared top bar component already supports requested control layout:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/SettingsTopAppBar.kt:23`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Layout.kt:61`
- Nav routes currently include `settings`, `layouts`, `orientation_settings`:
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt:74`
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt:139`
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt:141`
- ADS-b preferences currently store only enabled/disabled:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt:20`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt:26`
- ADS-b overlay icon sizing is hardcoded:
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt:55`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt:191`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt:192`
- Overlay lifecycle creation points that must inherit configured size:
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt:167`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt:131`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt:158`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt:183`
- Runtime ADS-b target updates already flow through overlay manager:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt:193`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt:190`

## 4) Architecture Contract

### 4.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-b overlay enabled | `AdsbTrafficPreferencesRepository` | `enabledFlow` via `AdsbTrafficUseCase.overlayEnabled` | UI-local persistent mirrors |
| ADS-b icon size (new) | `AdsbTrafficPreferencesRepository` | `iconSizePxFlow` via `AdsbTrafficUseCase` | UI-only persistent state or `MapOverlayManager` as source of truth |
| Current rendered ADS-b icon size | `MapOverlayManager` runtime cache (non-authoritative) | imperative apply to `AdsbTrafficOverlay` | Saved preferences in UI layer |

### 4.2 Dependency Direction

- Preserved flow: `UI -> ViewModel -> UseCase -> PreferencesRepository`.
- No repository usage from Composables.
- `MapOverlayManager` remains UI runtime adapter only; no preference persistence in UI runtime classes.

### 4.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| ADS-b icon size setting | N/A (configuration value) | Not time-derived |
| Existing ADS-b polling timestamps | Monotonic | Existing repository behavior remains unchanged |

No new time-base comparisons are introduced.

### 4.4 Threading and Cadence

- DataStore updates: existing repository coroutine context.
- Map layer property updates: map/UI thread where overlays currently update.
- No new high-rate loops.

### 4.5 Replay Determinism

- No replay logic changes.
- Determinism unaffected (UI presentation setting only).

## 5) Data Flow (Before -> After)

Before:

`General Settings UI (no ADS-b page) -> N/A -> AdsbTrafficOverlay fixed constants (56px)`

After:

`General ADS-b Screen -> AdsbSettingsViewModel -> AdsbTrafficUseCase -> AdsbTrafficPreferencesRepository (DataStore SSOT) -> MapScreenViewModel (iconSize flow) -> MapScreenRoot/Scaffold wiring -> MapOverlayManager -> AdsbTrafficOverlay symbol/icon size`

## 6) Implementation Phases

### Phase 1: Settings Navigation + Screen Shell

- Goal:
  - Add `ADS-b` item to `General` and route to a dedicated screen with `Layouts`-style top bar.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt`
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt` (new)
- Exit criteria:
  - `General` shows `ADS-b`.
  - Route opens screen with left arrows and map icon using `SettingsTopAppBar`.

### Phase 2: Preferences + UseCase + ViewModel Wiring

- Goal:
  - Add persistent icon-size setting with clamped range and default.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt` (`AdsbTrafficUseCase`)
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsUseCase.kt` (new, thin wrapper)
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsViewModel.kt` (new)
- Exit criteria:
  - Setting is persisted and emitted as flow.
  - ViewModel exposes slider state and update action.

### Phase 3: Runtime Overlay Application

- Goal:
  - Apply icon-size changes to current and recreated ADS-b overlays.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
- Exit criteria:
  - Changing slider updates visible ADS-b icon size live.
  - Style reload/re-init keeps configured size.

### Phase 4: Tests + Docs

- Goal:
  - Lock behavior with tests and update architecture pipeline docs.
- Files to change:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepositoryTest.kt` (new)
  - `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Exit criteria:
  - New tests pass and pipeline doc includes ADS-b settings wiring.

## 7) Strict Closed-Loop Audit Protocol (Hard Exit Rule)

### Rule

- Passes must run in exact order.
- Any finding in any pass triggers:
  - immediate patch,
  - audit counter reset to Pass 1,
  - full re-run from Pass 1.
- Hard exit only when all passes complete with zero findings in one continuous run.

### Pass Order

1. Pass 1 - Architecture/Layers Audit
   - Validate CM-05, CM-12.
   - Confirm no UI direct repository access and no ViewModel architecture violation.
2. Pass 2 - Navigation/UI Contract Audit
   - Validate CM-01, CM-02, CM-03.
   - Verify route registration, General entry placement, top bar parity, slider bounds/default.
3. Pass 3 - SSOT/Persistence Audit
   - Validate CM-04.
   - Verify DataStore owner, clamping, and restore behavior.
4. Pass 4 - Map Runtime Audit
   - Validate CM-06, CM-07, CM-08.
   - Verify live resize, style-change resilience, and tap behavior after resize.
5. Pass 5 - Tests/Docs/Build Audit
   - Validate CM-09, CM-10, CM-11.
   - Run required Gradle checks and confirm doc update.

## 8) Test Plan

- Unit tests:
  - Add repository test for default/clamp/persist of icon size.
  - Add/extend VM tests for icon-size flow and setter propagation.
- Replay/regression tests:
  - Not applicable (no replay logic change), but replay smoke should remain unchanged.
- UI/instrumentation tests:
  - Manual check: `General -> ADS-b`, slider drag min/max, return to map, icon size changes.
  - Manual check: change map style and confirm size retained.
- Degraded/failure-mode tests:
  - Values below/above bounds are clamped to `50` and `124`.

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

## 9) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Icon/label overlap at max size | Reduced readability | Validate max-size UI; if overlap is unacceptable, update label offset scaling in same change | XCPro Team |
| Overlay recreated with default size | Inconsistent UX | Cache latest size in overlay manager and re-apply on each overlay creation | XCPro Team |
| Slider spams map property updates | Jank on low-end devices | Debounce or only commit snapped int values; avoid expensive bitmap recreation per tick | XCPro Team |

## 10) Acceptance Gates

- All compliance matrix IDs CM-01..CM-12 pass.
- No architecture rule violations.
- No new deviation entry unless explicitly approved.
- Hard exit condition satisfied (all audit passes green in one uninterrupted cycle).

## 11) Rollback Plan

- Revert independently:
  - UI route/screen additions.
  - Preference key and use-case wiring.
  - Overlay dynamic icon-size application.
- Recovery:
  - Keep repository key if already released, but revert runtime usage to default `56` and hide UI entry in a follow-up patch.
