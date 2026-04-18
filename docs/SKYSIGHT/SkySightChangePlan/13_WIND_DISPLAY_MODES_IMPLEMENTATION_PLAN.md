# SkySight Wind Display Modes Implementation Plan

Date: 2026-02-16
Owner: XCPro Team
Status: In progress

## Purpose

Implement selectable SkySight wind display modes in XCPro map forecast overlays.
Current wind rendering is hardcoded to arrow symbols. This plan adds user-selectable render modes while preserving MVVM + UDF + SSOT and current map overlay stability.

Read first:

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`
6. `docs/SKYSIGHT/SkySightChangePlan/12_STAGE_B_IMPLEMENTATION_PLAN.md`

## 0) Metadata

- Title: SkySight Wind Display Modes
- Issue/PR: TBD
- Related docs: `docs/refactor/SkySight_Wind_Overlay_Refactor_Plan_2026-02-15.md`

## 0.1 Deep-Dive Current State (Codebase)

Observed in current code:

- Wind data path is already provider-backed and explicit:
  - `SkySightForecastProviderAdapter` emits wind tile specs with `format=VECTOR_WIND_POINTS`, `speedProperty=spd`, `directionProperty=dir`.
  - File: `feature/map/src/main/java/com/trust3/xcpro/forecast/SkySightForecastProviderAdapter.kt`
- Forecast UI/runtime path is fully wired:
  - `ForecastPreferencesRepository` -> `ForecastOverlayRepository` -> `ForecastOverlayViewModel` -> `ForecastOverlayBottomSheet` -> `MapScreenContent` -> `MapOverlayManager` -> `ForecastRasterOverlay`.
- Wind rendering is currently hardcoded:
  - One custom arrow bitmap (`WIND_ARROW_ICON_ID`) in `ForecastRasterOverlay`.
  - One symbol layer + one circle layer.
  - No style mode selection in preferences or UI.
  - File: `feature/map/src/main/java/com/trust3/xcpro/map/ForecastRasterOverlay.kt`
- Existing wind UI control is size-only:
  - `windOverlayScale` persisted in preferences and exposed in bottom sheet.
  - File: `feature/map/src/main/java/com/trust3/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
- Forecast settings page (General) currently has:
  - enable, opacity, region, credentials, auth confirmation.
  - no wind display mode control.
  - File: `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`
- No renderer tests exist for `ForecastRasterOverlay` branch behavior.

Conclusion:

- SkySight API provides wind vectors (`spd`, `dir`), not presentation mode.
- Wind mode choice is a client-side render concern and should be owned by XCPro preferences + map runtime.

## 0.2 Additional Misses Found (Deep Dive 2026-02-16)

These gaps were not fully captured in the first pass and should be handled during implementation:

1. Auth check path is not coupled to overlay fetch path.
   - `ForecastAuthRepository` verifies `/api/auth` with `X-API-KEY`, but overlay data calls in
     `SkySightForecastProviderAdapter` do not use saved credentials or auth-derived session material.
   - Files:
     - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastAuthRepository.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/forecast/SkySightForecastProviderAdapter.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`

2. MapLibre HTTP client is globally replaced at runtime.
   - `SkySightMapLibreNetworkConfigurator` calls `HttpRequestUtil.setOkHttpClient(client)` and injects
     `Origin` only for selected SkySight hosts.
   - This is process-global for MapLibre and should be treated as a shared-runtime risk.
   - Files:
     - `feature/map/src/main/java/com/trust3/xcpro/map/SkySightMapLibreNetworkConfigurator.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt`

3. Forecast and ADS-b share one unqualified OkHttp DI binding.
   - `AdsbNetworkModule` provides a singleton `OkHttpClient` used by ADS-b and forecast repositories.
   - Wind mode work should avoid increasing coupling; consider qualifier split if behavior diverges.
   - Files:
     - `feature/map/src/main/java/com/trust3/xcpro/di/AdsbNetworkModule.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/forecast/SkySightForecastProviderAdapter.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastAuthRepository.kt`

4. Auto-time ticker runs continuously even in manual time mode.
   - `autoTimeTickerFlow()` loops forever and drives selection flow every minute.
   - This is acceptable for MVP but is a battery/cadence optimization gap.
   - File:
     - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRepository.kt`

5. Forecast time labels are device-timezone based, not region-timezone based.
   - `formatForecastTime()` uses `ZoneId.systemDefault()`.
   - For region-driven forecast interpretation, this is a UX correctness gap.
   - File:
     - `feature/map/src/main/java/com/trust3/xcpro/map/ui/ForecastOverlayBottomSheet.kt`

6. Wind point query exposes speed only, not direction.
   - Evidence payload includes both `sfcwindspd/sfcwinddir` and `bltopwindspd/bltopwinddir`, but
     adapter maps only speed fields via `resolvePointField`.
   - Wind mode implementation should decide and document callout semantics.
   - Files:
     - `feature/map/src/main/java/com/trust3/xcpro/forecast/SkySightForecastProviderAdapter.kt`
     - `docs/integrations/skysight/evidence/value_success.json`

7. No dedicated renderer tests for `ForecastRasterOverlay`.
   - Wind mode branching (ARROW/BARB) adds complexity to source/layer cleanup and icon setup.
   - Missing tests are a concrete regression risk.

## 0.3 Additional Misses Found (Deep Dive Round 2, 2026-02-16)

1. Release build strips SkySight/OpenSky keys to empty strings.
   - In `feature/map/build.gradle.kts`, `debug` reads properties but `release` hardcodes:
     - `SKYSIGHT_API_KEY = ""`
     - `OPENSKY_CLIENT_ID = ""`
     - `OPENSKY_CLIENT_SECRET = ""`
   - If release builds are used for real flying, auth-backed integrations will fail by config.

2. Overlay-disabled path still resolves selection each tick.
   - `ForecastOverlayRepository` computes `resolveSelection(...)` before checking `overlayEnabled`.
   - This currently calls `catalogPort.getParameters()` and `catalogPort.getTimeSlots(...)` even when overlay is off.
   - With a future network-backed catalog, this becomes background load while disabled.

3. Legend cache key uses UTC day bucket, not region-local day.
   - `dayBucketUtc = selectedTimeSlot.validTimeUtcMs / DAY_MS` can split one local forecast day into two cache buckets for regions far from UTC.
   - Effect: unnecessary legend refetches and avoidable rate-limit pressure.

4. Opacity and wind-size sliders write preferences continuously while dragging.
   - Bottom sheet and Forecast settings both call setters on every slider move.
   - This creates high-frequency DataStore writes and extra state churn; mode additions should avoid repeating this pattern.

5. Wind-point UX semantics are still incomplete.
   - Wind point payload includes speed and direction, but current query path surfaces speed only.
   - If display mode includes barbs or other directional glyphs, callout semantics should be explicitly defined (speed-only vs speed+dir).

6. Forecast settings state surface is narrower than overlay state surface.
   - `ForecastSettingsUseCase/ViewModel` currently expose only enable/opacity/region and credentials/auth test.
   - Wind display mode needs explicit settings use-case wiring or settings and map-sheet controls will drift.

## 1) Scope

- Problem statement:
  - Wind overlay is visible but fixed to arrows. Users need alternate visualizations for readability in flight.
- Why now:
  - Wind overlay is implemented and used, and visual legibility is now the key blocker.
- In scope:
  - Add persistent wind display mode setting.
  - Add map wind modes:
    - `ARROW` (current behavior, default)
    - `BARB` (meteorological barb glyphs)
  - Expose mode selection in forecast overlay bottom sheet.
  - Expose mode selection in Forecast settings General section.
  - Thread mode through repository/viewmodel/runtime layers.
  - Keep current wind size slider behavior and make it mode-aware.
- Out of scope:
  - Streamline or particle animations.
  - Any SkySight API changes.
  - New backend/proxy services.
- User-visible impact:
  - Pilot can choose how wind speed/direction is displayed on map.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Wind display mode | `ForecastPreferencesRepository` | `Flow<ForecastWindDisplayMode>` | Compose-only local mode state treated as authoritative |
| Wind overlay scale | `ForecastPreferencesRepository` | `Flow<Float>` | Runtime-only copies as source of truth |
| Active forecast overlay state | `ForecastOverlayRepository` | `Flow<ForecastOverlayUiState>` | VM/runtime-managed overlay state mirrors |
| Wind render branch config | `ForecastOverlayUiState` -> `MapOverlayManager` -> `ForecastRasterOverlay` | method args | Runtime inference from parameter IDs only |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/usecase -> data`

Modules/files touched (planned):

- `feature/map/src/main/java/com/trust3/xcpro/forecast/*`
- `feature/map/src/main/java/com/trust3/xcpro/map/ForecastRasterOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ForecastSettings*`
- `feature/map/src/test/java/com/trust3/xcpro/forecast/*`

Boundary risk:

- Keep all MapLibre symbol/circle/image logic in runtime map classes only.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Wind style selection persistence | none (implicit hardcoded arrow) | `ForecastPreferencesRepository` | SSOT and process-safe persistence | Preferences unit tests |
| Wind style propagation to UI | none | `ForecastOverlayRepository` + `ForecastOverlayViewModel` | UDF state-driven UI | Repository and VM tests |
| Wind style rendering branch | hardcoded arrow in `ForecastRasterOverlay` | `ForecastRasterOverlay` mode switch by explicit enum | avoid hidden behavior | runtime/manual tests |
| Settings screen control | none | `ForecastSettingsUseCase/ViewModel/Screen` | keep General settings parity | UI/manual test |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | no direct manager bypass found for forecast wind style | N/A | N/A |

### 2.3 Time Base

No new time-dependent domain logic is introduced.
Existing forecast slot behavior remains wall-time based.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Wind display mode | N/A | static preference value |
| Wind glyph selection by speed | data-driven, not clock-driven | derived from tile feature property |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Preferences/repository: existing IO dispatcher paths.
  - Map style updates and layer/image mutations: main/UI runtime.
- Cadence:
  - No new polling loops.
  - No per-frame network additions.
  - Render mode changes trigger same overlay re-render path as existing parameter/time changes.
- Latency target:
  - Mode switch visible in under 250 ms after state propagation on typical device.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness: No.
- Replay/live divergence: unchanged. This is visual-only map overlay rendering.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| ViewModel starts owning map/runtime logic | ARCHITECTURE.md dependency direction | review + enforceRules | changed files in `forecast/*` vs `map/*` |
| Duplicate state ownership for wind mode | SSOT rules | unit tests | `ForecastPreferencesRepositoryTest` |
| Non-lifecycle UI state collection regressions | CODING_RULES lifecycle collection | lint + review | forecast settings and bottom sheet wiring |
| Runtime layer leaks on mode switching | map runtime stability | manual + runtime assertions | `ForecastRasterOverlay` behavior checks |
| Performance regressions from icon creation | threading/perf rules | review + manual profiling | `ForecastRasterOverlay` |

## 3) Data Flow (Before -> After)

Before:

`ForecastPreferencesRepository (windOverlayScale) -> ForecastOverlayRepository -> ForecastOverlayUiState -> MapOverlayManager -> ForecastRasterOverlay (always arrow + circles)`

After:

`ForecastPreferencesRepository (windOverlayScale + windDisplayMode) -> ForecastOverlayRepository -> ForecastOverlayUiState -> ForecastOverlayViewModel -> BottomSheet/Settings intents -> MapOverlayManager -> ForecastRasterOverlay mode branch (ARROW | BARB)`

## 4) Implementation Phases

### Phase 0 - Contract and Types

- Goal:
  - Add provider-neutral enum and settings contract.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayModels.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastSettings.kt`
- Changes:
  - Add `ForecastWindDisplayMode` enum.
  - Add default constant and parse/normalize helper.
  - Extend `ForecastOverlayUiState` with `windDisplayMode`.
- Tests:
  - new enum normalization tests (if helper added).
- Exit:
  - build compiles with new type contract.

### Phase 1 - SSOT and UseCase/VM Wiring

- Goal:
  - Persist and expose wind display mode via existing forecast state pipeline.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastPreferencesRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayUseCases.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayViewModel.kt`
- Changes:
  - Add datastore key `forecast_wind_display_mode`.
  - Add `setWindDisplayMode(...)` and `windDisplayModeFlow`.
  - Include mode in `ForecastOverlayUiState` emission.
  - Add dedicated use case and VM intent method.
- Tests:
  - update `ForecastPreferencesRepositoryTest`.
  - update `ForecastOverlayRepositoryTest` state assertions.
- Exit:
  - mode survives restart and reaches VM state.

### Phase 2 - UI Controls (Bottom Sheet + General Settings)

- Goal:
  - User can change wind mode from map forecast sheet and Forecast settings.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ForecastSettingsUseCase.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`
- Changes:
  - Add mode chips/toggle in bottom sheet.
  - Add mode selector in Forecast General card.
  - Keep both controls bound to same SSOT.
- Tests:
  - VM/usecase tests for mode mutation.
- Exit:
  - mode is editable from both entry points and remains in sync.

### Phase 3 - Runtime Renderer Branches

- Goal:
  - Render ARROW and BARB branches with safe cleanup and style reload.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ForecastRasterOverlay.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
- Changes:
  - Pass mode through `setForecastOverlay(...)` and reapply paths.
  - `ForecastRasterOverlay.render(...)` accepts `windDisplayMode`.
  - ARROW:
    - preserve current behavior.
  - BARB:
    - add precomputed barb glyph image set keyed by speed bucket.
    - choose icon via speed-step expression.
    - rotate by `dir` with `iconRotationAlignment("map")`.
  - Ensure full layer/source cleanup on branch changes and style reload.
- Tests:
  - add pure logic tests for speed-to-barb bucket mapping.
  - manual smoke for layer branch switching and style reload.
- Exit:
  - all three modes render correctly and switch without orphan layers.

### Phase 4 - Hardening and Docs Sync

- Goal:
  - Close quality gaps and document behavior.
- Files:
  - `docs/ARCHITECTURE/PIPELINE.md` (if flow text changes)
  - `docs/SKYSIGHT/SkySightChangePlan/13_WIND_DISPLAY_MODES_IMPLEMENTATION_PLAN.md` (status update)
- Changes:
  - verify no architecture drift.
  - verify no performance regressions.
  - finalize manual verification checklist.
- Exit:
  - required verification commands pass.

## 5) Test Plan

- Unit tests:
  - `ForecastPreferencesRepositoryTest`:
    - default mode.
    - persisted mode.
    - invalid stored value normalizes to default.
  - `ForecastOverlayRepositoryTest`:
    - mode propagates into emitted `ForecastOverlayUiState`.
  - `ForecastOverlayViewModel` tests:
    - mode intent updates preferences path.
  - new renderer helper tests:
    - speed bucket mapping for barb icon IDs.
- Replay/regression:
  - existing replay behavior should remain unchanged (no domain/fusion changes).
- UI/instrumentation (when device available):
  - change mode from bottom sheet and verify map updates.
  - change mode in Forecast settings and verify map sheet reflects same mode.
  - style change/reload preserves selected mode rendering.
- Degraded/failure-mode:
  - if symbol image registration fails, renderer must fail safe without crash and keep map responsive.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when device is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Barb glyph orientation interpreted incorrectly (`from` vs `to`) | wrong pilot interpretation | lock one orientation rule and verify against known sample points | XCPro Team |
| Too many runtime image registrations on style reload | UI jank | register once per style, bounded icon set only | XCPro Team |
| Layer cleanup misses old branch IDs | visual artifacts | explicit remove calls for all wind layer IDs on each branch switch | XCPro Team |
| Settings and sheet mode drift | confusing UX | both mutate same `ForecastPreferencesRepository` field | XCPro Team |

## 7) Acceptance Gates

- No ARCHITECTURE.md or CODING_RULES.md violations.
- Single SSOT for wind display mode in `ForecastPreferencesRepository`.
- ViewModel/usecase boundaries preserved.
- Map runtime remains owner of MapLibre APIs.
- Existing arrow mode remains default and backward compatible.
- Required checks pass.

## 8) Rollback Plan

- Revert runtime mode branching in `ForecastRasterOverlay` while keeping enum/types.
- Keep persisted mode key but map all values to `ARROW` until branch is reintroduced.
- If instability appears in flight testing, ship with ARROW-only UI and hide other modes behind a feature flag.
