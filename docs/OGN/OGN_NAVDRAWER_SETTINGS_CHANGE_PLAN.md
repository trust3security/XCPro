# OGN Nav Drawer Settings Change Plan (Template-Compliant)

> Historical implementation record (2026-02-11). For current runtime behavior, use:
> - `docs/OGN/OGN.md`
> - `docs/OGN/OGN_PROTOCOL_NOTES.md`
> - `docs/ARCHITECTURE/PIPELINE.md`

## 0) Metadata

- Title: OGN General Settings Page + Glider Icon Size Slider
- Owner: XCPro Team
- Date: 2026-02-11
- Issue/PR: TBD
- Status: Implemented (required checks pass; manual map QA pending)

## 1) Scope

- Problem statement:
  OGN currently supports overlay enable/disable only. There is no `General -> OGN` settings page and no persisted OGN icon-size control.
- Why now:
  ADS-B already ships this pattern and users requested OGN parity for settings UX and map marker scaling.
- In scope:
  - Add `General -> OGN` entry and route.
  - Add OGN settings screen with slider.
  - Persist OGN icon size in DataStore.
  - Wire OGN icon size through use-case/viewmodel into map runtime overlay.
  - Ensure icon size survives map style changes and process restart.
  - Preserve OGN viewport culling and stale-alpha semantics while changing marker rendering.
  - Preserve OGN target cap and stable per-target label behavior.
  - Add/keep OGN track-aware icon rotation when track is available.
  - Preserve OGN layer ordering relative to ownship/blue-location overlay.
  - Scale OGN label offset with icon size to avoid overlap across min/max slider values.
- Out of scope:
  - OGN APRS parsing/network/reconnect behavior.
  - OGN traffic filtering policy.
  - New OGN marker details/tap UX.
- User-visible impact:
  - New OGN settings page in General.
  - Live slider to increase/decrease OGN glider icon size on map.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN overlay enabled preference | `OgnTrafficPreferencesRepository` | `Flow<Boolean>` | UI-local toggle mirrors |
| OGN icon size preference (px) | `OgnTrafficPreferencesRepository` | `Flow<Int>` | UI/runtime mutable copies as authority |
| OGN traffic targets/snapshot | `OgnTrafficRepository` | `StateFlow<List<OgnTrafficTarget>>`, `StateFlow<OgnTrafficSnapshot>` | UI-side target stores |
| Runtime applied OGN icon size | `MapOverlayManager` runtime cache (non-SSOT, derived) | imperative runtime apply | treating runtime cache as authoritative preference |

### 2.2 Dependency Direction

Dependency direction remains:

`UI -> domain -> data`

- Modules/files touched:
  - UI: nav drawer settings screen and route
  - Domain/use-case: `OgnTrafficUseCase` and OGN settings use-case
  - Data: `OgnTrafficPreferencesRepository`
  - Runtime UI map: `MapScreenViewModel`, bindings, overlay manager, OGN overlay
- Any boundary risk:
  - Direct repository writes from composables.
  - Runtime overlay state becoming hidden SSOT.
  - Importing the wrong `MapOverlayManager` symbol (`com.example.xcpro.screens.overlays.MapOverlayManager` vs `com.example.xcpro.map.MapOverlayManager`).

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN marker size value source | Hardcoded constant in `OgnTrafficOverlay` | `OgnTrafficPreferencesRepository` via use-case/viewmodel flow | User-configurable and restart-persistent | Repository + VM tests, manual map verification |
| OGN settings UI entrypoint | None | `Settings-df.kt` + `AppNavGraph.kt` + `OgnSettingsScreen.kt` | Discoverable General settings parity with ADS-B | Navigation/manual QA |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| OGN settings writes (new surface) | N/A (screen does not exist yet) | `OgnSettingsScreen -> OgnSettingsViewModel -> OgnSettingsUseCase -> OgnTrafficPreferencesRepository` | Phase 1-2 |
| OGN icon size runtime apply (new flow) | N/A (no icon size flow exists) | `MapScreenViewModel.ognIconSizePx -> MapScreenBindings -> MapScreenRoot/ScaffoldInputs -> MapOverlayManager.setOgnIconSizePx` | Phase 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| OGN preference read/write timestamps | Wall (storage timing only) | DataStore persistence lifecycle only |
| OGN stale visual alpha in overlay | Monotonic (unchanged) | Existing OGN runtime stale shading behavior |
| OGN repository network/reconnect timing | Injected clock (existing behavior) | Deterministic/testable repository timing contract |

Explicitly forbidden comparisons:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - UI/Compose + MapLibre layer mutation: `Main`
  - Preference I/O and OGN repository network: `IO`
  - No heavy compute introduced on `Main`
- Primary cadence/gating sensor:
  - OGN center updates are GPS-driven from `mapLocation` in `MapScreenTrafficCoordinator`.
- Hot-path latency budget:
  - Slider change to icon-size style apply: target <= 50 ms typical

### 2.5 Replay Determinism

- Deterministic for same input: Yes (unchanged replay pipeline behavior)
- Randomness used: No new randomness
- Replay/live divergence rules:
  - OGN remains optional/overlay-only and non-authoritative for flight/replay truth.
  - This change must not alter replay fusion/metrics outputs.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| UI writes repository directly | MVVM/UDF/SSOT | Review + architecture check | `OgnSettingsScreen.kt`, `OgnSettingsViewModel.kt`, `OgnSettingsUseCase.kt` |
| Icon size out-of-range | SSOT correctness | Unit test | `OgnTrafficPreferencesRepositoryTest.kt` |
| ViewModel fails to expose persisted icon size | VM boundary contract | Unit test | `MapScreenViewModelTest.kt` |
| Style change loses configured icon size | Runtime regression | Integration-style VM/runtime verification + manual QA | `MapOverlayManager.kt`, `MapScreenRoot.kt`, manual style-switch test |
| Wrong overlay manager import path | Layer/runtime correctness | Review | `MapScreenManagers.kt`, `MapScreenRootHelpers.kt`, `MapRuntimeController.kt`, `MapScreenRoot.kt` |
| Patching dead init path only (`initializeOverlays`) | Runtime regression | Review + manual QA | `MapInitializer.kt`, `MapScreenScaffoldInputs.kt`, `MapOverlayManager.kt` |
| OGN icon image id collides with ADS-B style images | Runtime overlay isolation | Review + manual QA | `OgnTrafficOverlay.kt`, `AdsbTrafficOverlay.kt` |
| Pixel slider value applied as raw MapLibre iconScale | UX correctness | Review + unit-style math check | `OgnTrafficOverlay.kt`, `OgnIconSizing.kt` |
| Label overlap/regression at min/max icon sizes | UX/runtime correctness | Manual QA + review | `OgnTrafficOverlay.kt` |
| Layer order regression hides OGN markers/labels under wrong layers | Runtime correctness | Manual QA + review | `OgnTrafficOverlay.kt` |
| Pipeline docs drift | Documentation sync rule | Review gate | `docs/ARCHITECTURE/PIPELINE.md` |

## 3) Data Flow (Before -> After)

Before:

`General settings (no OGN page) -> no OGN icon-size preference -> OgnTrafficOverlay fixed marker size`

After:

`General -> OGN -> OgnSettingsScreen -> OgnSettingsViewModel -> OgnSettingsUseCase -> OgnTrafficPreferencesRepository.iconSizePxFlow -> OgnTrafficUseCase.iconSizePx -> MapScreenViewModel.ognIconSizePx -> MapScreenBindings -> MapScreenRoot/ScaffoldInputs -> MapOverlayManager.setOgnIconSizePx -> OgnTrafficOverlay.setIconSizePx -> MapLibre symbol iconSize`

## 4) Implementation Phases

### Phase 1
- Goal:
  Add `General -> OGN` settings entry and dedicated screen shell.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt`
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsScreen.kt` (new)
- Tests to add/update:
  - Navigation/manual QA checklist only in this phase.
- Exit criteria:
  - Route is reachable and top bar behavior matches settings pattern.

### Phase 2
- Goal:
  Add OGN icon-size preference SSOT and layer-correct settings write path.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnIconSizing.kt` (new)
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsUseCase.kt` (new)
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsViewModel.kt` (new)
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
- Tests to add/update:
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt` (new)
- Exit criteria:
  - OGN icon-size preference clamps, persists, and is observable as flow.

### Phase 3
- Goal:
  Apply OGN icon size live in runtime overlay and preserve after overlay recreation/style changes.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` (only if init-order patch is required)
- Tests to add/update:
  - `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt` (OGN icon-size exposure assertions)
- Exit criteria:
  - Changing slider updates OGN icon size on live map without restart.
  - Size remains after style switch and map re-init path.
  - Size reapply is verified on active runtime paths (`onMapReady`, `LaunchedEffect`, `onMapStyleChanged`), not only on `MapOverlayManager.initializeOverlays(...)`.
  - OGN viewport culling (`OgnSubscriptionPolicy.isInViewport`) remains in effect after overlay refactor.
  - OGN style image IDs are unique to OGN overlay, so OGN cleanup cannot remove ADS-B style images.
  - Icon-size slider semantics are pixel-based (`124..512`) and converted to MapLibre `iconSize` scale via a fixed base bitmap size.
  - OGN target cap remains enforced after icon migration.
  - OGN label offset scales with icon size so labels remain readable at min/max values.
  - OGN layer insertion order remains stable relative to blue ownship layer.

### Phase 4
- Goal:
  Final verification, docs sync, and release hardening.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/OGN/OGN_NAVDRAWER_SETTINGS_CHANGE_PLAN.md` (status updates)
- Tests to add/update:
  - Any missing regression coverage discovered in verification.
- Exit criteria:
  - All required checks pass and compliance matrix is closed.

## 5) Test Plan

- Unit tests:
  - OGN icon-size default/clamp/persist in repository test.
  - ViewModel exposes persisted OGN icon size as state flow.
- Replay/regression tests:
  - Confirm no replay pipeline behavior change (existing replay tests stay green).
- UI/instrumentation tests (if needed):
  - Optional nav route smoke and slider interaction instrumentation.
- Degraded/failure-mode tests:
  - OGN overlay absent/map not ready while slider changes; verify no crash and apply on ready.
  - Style switch after size change; verify reapply behavior.
  - Confirm path-specific apply order: `MapInitializer.setupOverlays(...)` then `MapScreenScaffoldInputs.onMapReady(...)` reapply.
  - Verify OGN cleanup does not affect ADS-B icons when both overlays are active.
  - Verify label readability and no overlap at slider min (`50`) and max (`124`).
- Boundary tests for removed bypasses:
  - Screen writes via VM/use-case only; no direct repository mutation in composables.

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
| OGN currently uses circle markers; icon migration may alter readability/perf | Medium | Keep label halo and stale alpha, cap targets, test with dense traffic | XCPro Team |
| Style reload can reset icon size | High | Cache configured size in `MapOverlayManager` and reapply on style/map ready | XCPro Team |
| Slider spam causes jank | Medium | Snap-to-int, no-op on unchanged value, clamp in repository and runtime | XCPro Team |
| UI-domain boundary regression | High | Keep strict `UI -> VM -> UseCase -> Repository` path and review callsites | XCPro Team |
| Wrong `MapOverlayManager` import during implementation | Medium | Enforce `com.example.xcpro.map.MapOverlayManager` in runtime wiring files | XCPro Team |
| OGN/ADS-B style image cleanup interference | High | Use distinct style image IDs and cleanup only overlay-owned IDs | XCPro Team |
| Incorrect icon-size scaling semantics | Medium | Define fixed bitmap base size and convert px to scale consistently | XCPro Team |
| Label overlap at large icon sizes | Medium | Tie text offset to icon scale and verify min/max states manually | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling remains explicit and unchanged in domain paths
- Replay behavior remains deterministic
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry)

Execution compliance matrix for this change:

| ID | Requirement | Evidence Target | Status |
|---|---|---|---|
| CM-01 | Add `OGN` tile under `General` and route to dedicated screen | `Settings-df.kt`, `AppNavGraph.kt` | PASS |
| CM-02 | OGN settings screen uses `SettingsTopAppBar` pattern | `OgnSettingsScreen.kt` | PASS |
| CM-03 | Slider range exactly `124..512` and default `124` | `OgnIconSizing.kt`, `OgnSettingsScreen.kt`, `OgnTrafficPreferencesRepository.kt` | PASS |
| CM-04 | OGN icon size persisted and restored from DataStore | `OgnTrafficPreferencesRepository.kt` | PASS |
| CM-05 | Layer-correct path (`UI -> VM -> UseCase -> PreferencesRepository`) | `OgnSettingsViewModel.kt`, `OgnSettingsUseCase.kt`, `MapScreenUseCases.kt` | PASS |
| CM-06 | OGN marker/icon size updates live without restart | `MapScreenViewModel.kt`, `MapScreenBindings.kt`, `MapScreenRoot.kt`, `MapOverlayManager.kt`, `OgnTrafficOverlay.kt` | CODE PASS / MANUAL PENDING |
| CM-07 | Configured size reapplies after style change/map re-init | `MapScreenScaffoldInputs.kt`, `MapOverlayManager.kt`, `MapInitializer.kt` | CODE PASS / MANUAL PENDING |
| CM-08 | OGN labels/stale visuals remain intact after icon-size change | `OgnTrafficOverlay.kt` | CODE PASS / MANUAL PENDING |
| CM-09 | Regression tests cover clamp/default/persist + VM exposure | OGN prefs test + VM test | PASS |
| CM-10 | Required repo checks pass | Gradle tasks | PASS |
| CM-11 | Pipeline docs include OGN settings wiring | `docs/ARCHITECTURE/PIPELINE.md` | PASS |
| CM-12 | No architecture deviation introduced | `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` | PASS |
| CM-13 | Implementation patches active runtime apply paths (not dead `initializeOverlays`) | `MapScreenScaffoldInputs.kt`, `MapScreenRoot.kt`, `MapOverlayManager.kt` | PASS |
| CM-14 | OGN viewport culling and stale visuals preserved after icon refactor | `OgnTrafficOverlay.kt` | PASS |
| CM-15 | OGN style image IDs are unique; OGN cleanup cannot remove ADS-B icon images | `OgnTrafficOverlay.kt`, `AdsbTrafficOverlay.kt` | PASS |
| CM-16 | Slider px value converts to MapLibre icon scale via fixed base size (not raw scale) | `OgnTrafficOverlay.kt`, `OgnIconSizing.kt` | PASS |
| CM-17 | OGN track-based icon rotation and target cap are preserved after icon migration | `OgnTrafficOverlay.kt` | PASS |
| CM-18 | OGN label offset scales with icon size and remains readable at min/max slider values | `OgnTrafficOverlay.kt` | CODE PASS / MANUAL PENDING |
| CM-19 | OGN layer order remains correct relative to ownship/blue-location overlay | `OgnTrafficOverlay.kt` | CODE PASS / MANUAL PENDING |

## 8) Rollback Plan

- What can be reverted independently:
  - OGN settings route/tile and settings screen.
  - OGN icon-size preference key consumption.
  - OGN overlay icon-size runtime application.
- Recovery steps if regression is detected:
  - Keep persisted key as inert data, revert runtime to fixed-size rendering, and hide OGN settings entry in a hotfix if needed.

## 9) Open Decisions

- `docs/OGN/OGN.md` also defines General settings for FLARM identity fields (`Registration`, `Competition ID`, `FLARM ID`).
- This change plan is scoped to OGN icon-size parity with ADS-B. Confirm whether FLARM identity fields ship in the same release or a separate follow-up plan.

## 10) Strict Closed-Loop Audit Protocol (Hard Exit Rule)

- Passes run in fixed order and any finding triggers patch + restart from pass 1.
- Hard exit only when all passes are green in one continuous run.

Pass order:
1. Architecture/layer audit (`UI -> VM -> UseCase -> Repository` and no wrong `MapOverlayManager` import).
2. Navigation/UI contract audit (`General -> OGN`, settings top bar parity, slider bounds/default).
3. SSOT/persistence audit (DataStore key/default/clamp/restore).
4. Runtime overlay audit (live sizing, style-recreate resilience, unique style image IDs, label offset scaling, layer order).
5. Tests/docs/build audit (`enforceRules`, `testDebugUnitTest`, `assembleDebug`, `PIPELINE.md` update).

## 11) Current Verification Snapshot (2026-02-11)

- Completed:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.ogn.OgnTrafficPreferencesRepositoryTest" --tests "com.example.xcpro.map.MapScreenViewModelTest.ognIconSize_defaultsToConfiguredDefaultPx" --tests "com.example.xcpro.map.MapScreenViewModelTest.ognIconSize_readsPersistedPreferenceOnInit"` (PASS)
  - `./gradlew :app:compileDebugKotlin` (PASS)
  - `./gradlew enforceRules` (PASS)
  - `./gradlew testDebugUnitTest` (PASS)
  - `./gradlew assembleDebug` (PASS)
- Remaining:
  - Manual map checks for slider live behavior, style-change resilience, and min/max label readability.

