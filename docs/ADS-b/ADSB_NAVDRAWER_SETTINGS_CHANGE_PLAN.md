# ADS-b Nav Drawer Settings Change Plan

## 0) Metadata

- Title: ADS-b General Settings Page + Map Icon Size Slider
- Owner: XCPro Team
- Date: 2026-02-10
- Last Updated: 2026-02-11
- Issue/PR: TBD
- Status: Implemented (hard-exit audit rerun in progress after final runtime patch)

## 1) Fixed Compliance Matrix (Locked + Execution Status)

| ID | Requirement | Evidence Location | Status | Verification Result |
|---|---|---|---|---|
| CM-01 | New `ADS-b` entry exists under `General` and opens a dedicated screen | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt`, `app/src/main/java/com/example/xcpro/AppNavGraph.kt` | PASS | Route `adsb_settings` added and wired from `General` |
| CM-02 | New `ADS-b` screen uses `SettingsTopAppBar` pattern (left arrows + map icon + same color scheme) like `Layouts` | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt` | PASS | Uses `SettingsTopAppBar` with same nav affordances as `Layouts` |
| CM-03 | Slider range is exactly `50..124` px and default is `56` px | `feature/map/src/main/java/com/example/xcpro/adsb/AdsbIconSizing.kt`, `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt`, `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt` | PASS | Min/default/max constants and clamped slider implemented |
| CM-04 | ADS-b icon size is persisted (DataStore SSOT) and restored after restart | `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt` | PASS | DataStore key `adsb_icon_size_px` with flow + setter |
| CM-05 | Flow remains layer-correct (`UI -> ViewModel -> UseCase -> PreferencesRepository`) | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsViewModel.kt`, `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsUseCase.kt`, `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt` | PASS | No UI direct repository access |
| CM-06 | MapLibre ADS-b icons resize live when setting changes (without requiring app restart) | `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`, `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`, `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt` | PASS | Flow-to-runtime wiring added; overlay applies `iconSize` dynamically |
| CM-07 | Icon size applies to newly recreated overlays (style changes, map re-init) | `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`, `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`, `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` | PASS | Reapply-on-ready path + recreated overlay map-binding fix applied |
| CM-08 | Tap-selection on ADS-b symbols still works after resize | `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt` | CODE PASS / MANUAL PENDING | Query layers unchanged (`ICON_LAYER_ID`, `LABEL_LAYER_ID`) |
| CM-09 | Regression tests cover clamp/default + VM wiring | `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepositoryTest.kt`, `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt` | PASS | Tests added for default, clamp, persist, VM observed value |
| CM-10 | Required repo checks pass | Gradle tasks | PARTIAL (latest rerun) | `enforceRules` passed on 2026-02-11; `testDebugUnitTest` was interrupted; `assembleDebug` rerun pending |
| CM-11 | Pipeline docs updated for new ADS-b settings-to-overlay wiring | `docs/ARCHITECTURE/PIPELINE.md` | PASS | ADS-b icon-size wiring documented |
| CM-12 | No untracked architecture deviation is introduced | `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` | PASS | No deviation entry required for this work |

## 2) Implemented Scope

- Added `ADS-b` tile in `General` settings grid.
- Added dedicated `ADS-b` settings page with `Layouts`-style top bar.
- Added first slider for ADS-b icon size with `50..124` bounds and default `56`.
- Persisted icon size in ADS-b DataStore preferences (SSOT).
- Wired setting into map runtime so icons update live and survive overlay recreation.
- Added tests for repository behavior and ViewModel observable value.
- Updated architecture pipeline documentation.

## 3) Completed Implementation Phases

### Phase 1: Settings Navigation + Screen Shell

Status: COMPLETED

Files:
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt`
- `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt` (new)

### Phase 2: Preferences + UseCase + ViewModel Wiring

Status: COMPLETED

Files:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbIconSizing.kt` (new)
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsUseCase.kt` (new)
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsViewModel.kt` (new)

### Phase 3: Runtime Overlay Application

Status: COMPLETED

Files:
- `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`

Audit-driven fixes applied in this phase:
- Removed early-return guard in `MapOverlayManager.setAdsbIconSizePx(...)` that could skip re-applying configured size to a newly recreated overlay.
- Ensured `MapInitializer` recreates ADS-b overlay with the current `MapLibreMap` instance during init to avoid stale map-handle reuse.

### Phase 4: Tests + Docs

Status: COMPLETED (implementation), VERIFICATION RERUN PENDING

Files:
- `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepositoryTest.kt` (new)
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt`
- `docs/ARCHITECTURE/PIPELINE.md`

## 4) Strict Closed-Loop Audit Log (Hard Exit Rule)

Rule:
- Passes run in order.
- Any finding triggers patch + restart from pass 1.
- Hard exit only when all passes are green in one continuous run.

Execution log:
1. Pass 1 - Architecture/Layers Audit: PASS
2. Pass 2 - Navigation/UI Contract Audit: PASS
3. Pass 3 - SSOT/Persistence Audit: PASS
4. Pass 4 - Map Runtime Audit: FINDING
   - Finding: recreated overlays could keep stale/default size in one path.
   - Patch applied: `MapOverlayManager`, `MapInitializer`.
   - Audit reset to Pass 1.
5. Pass 1 (rerun): PASS
6. Pass 5 - Tests/Docs/Build Audit:
   - `./gradlew enforceRules`: PASS (2026-02-11)
   - `./gradlew testDebugUnitTest`: interrupted by user during rerun
   - `./gradlew assembleDebug`: rerun pending after final runtime patch

Hard exit status:
- OPEN until `testDebugUnitTest` and `assembleDebug` reruns complete on the final patched state.

## 5) Remaining Verification To Close Hard Exit

Run:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Manual checks still recommended:
- `General -> ADS-b` screen open/navigation behavior.
- Slider min/max behavior at 50 and 124.
- Live map icon resize and style-switch resilience.
- ADS-b target tap-selection at min/max icon size.

## 6) Rollback Plan

- Revert independently:
  - UI route/screen additions.
  - Preference key and use-case wiring.
  - Overlay dynamic icon-size application.
- Recovery:
  - Keep repository key if already released, but revert runtime usage to default `56` and hide UI entry in a follow-up patch.
