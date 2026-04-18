# 17 Convergence Implementation Plan (SkySight -> XCPro)

Date: 2026-02-17
Owner: XCPro Team
Status: Ready for implementation

## Purpose

Provide a complete, architecture-compliant plan to implement SkySight
Convergence overlay in XCPro.

Convergence parameter id:

- `wblmaxmin`

## Scope

In scope:

- Add Convergence as selectable primary forecast overlay parameter.
- Render convergence tiles and legend through existing forecast overlay stack.
- Keep map long-press point query safe when convergence point field is not
  available.
- Preserve existing wind overlay behavior and multi-overlay architecture.

Out of scope:

- Changing backend auth strategy.
- Multi-primary stacking.
- Rewriting forecast runtime architecture.

## Preconditions

Confirmed:

- SkySight parameter exists: `wblmaxmin` (Convergence).
- SkySight app route exists: `/convergence`.

Not yet fully confirmed:

- Convergence point-value field name in `cf.skysight.io/point` response.

## SSOT and Architecture Contract

SSOT owners:

- Overlay preferences/state: `ForecastPreferencesRepository`.
- Overlay resolved runtime state: `ForecastOverlayRepository`.
- Provider contract mapping: `SkySightForecastProviderAdapter`.

Dependency direction:

- UI -> ViewModel -> UseCase -> Repository -> Provider ports.
- No UI/data boundary leaks.

Timebase:

- Forecast selection/time slots remain wall-clock UX state (existing forecast
  contract).
- No replay/fusion time changes.

Replay determinism:

- Deterministic for same inputs: yes.
- Randomness introduced: none.
- Live/replay divergence: none introduced by this change (forecast overlay only).

Boundary adapters:

- No new persistence/network boundary interfaces are introduced.
- Existing forecast ports remain the boundary:
  - `ForecastCatalogPort`
  - `ForecastTilesPort`
  - `ForecastLegendPort`
  - `ForecastValuePort`
- Data adapter remains `SkySightForecastProviderAdapter` bound through DI.

## Implementation Workstreams

## Phase 0 - Baseline lock

Tasks:

- Verify current forecast parameter catalog behavior and map rendering behavior.
- Add/confirm tests that lock current parameters and failure handling.

Files:

- `feature/map/src/test/java/com/trust3/xcpro/forecast/SkySightForecastProviderAdapterTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/forecast/ForecastOverlayRepositoryTest.kt`

Exit:

- Baseline tests pass before convergence edits.

## Phase 1 - Provider catalog support

Tasks:

- Add Convergence parameter meta entry to SkySight adapter catalog:
  - id: `wblmaxmin`
  - name: `Convergence`
  - category: use existing category convention (`Lift` or project-aligned).
  - unit: `m/s`.
- Ensure parameter appears in returned catalog and participates in
  parameter selection.

Files:

- `feature/map/src/main/java/com/trust3/xcpro/forecast/SkySightForecastProviderAdapter.kt`

Exit:

- Convergence appears in overlay parameter chips/list.

## Phase 2 - Tile/legend mapping

Tasks:

- Keep non-wind render path for `wblmaxmin`:
  - tile format: indexed fill (not wind points).
  - URL path: `.../{date}/{time}/wblmaxmin/{z}/{x}/{y}`.
- Ensure legend fetch resolves without special-case regressions.
- Keep source-layer candidate behavior compatible with existing fill products.

Files:

- `feature/map/src/main/java/com/trust3/xcpro/forecast/SkySightForecastProviderAdapter.kt`
- `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRepository.kt`

Exit:

- Convergence tile+legend load the same way as existing primary products.

## Phase 3 - Point-value safe fallback

Tasks:

- Do not hard-fail overlay when point-value query lacks convergence field.
- For convergence queries:
  - return unavailable/error status text that clearly says point value not
    available (until field mapping is confirmed), or
  - map field if proven in evidence.
- Ensure this affects callout/status only, not tile rendering.

Files:

- `feature/map/src/main/java/com/trust3/xcpro/forecast/SkySightForecastProviderAdapter.kt`
- `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayViewModel.kt`

Exit:

- Convergence map overlay works even when point-value field is missing.

## Phase 4 - UI and UX integration

Tasks:

- Confirm Convergence appears in forecast parameter selector.
- Confirm existing status text surfaces non-fatal convergence point-value
  unavailability.
- No new UI architecture paths; reuse existing UDF intent/state flows.

Files:

- `feature/map/src/main/java/com/trust3/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`

Exit:

- User can select Convergence and see overlay with clear status behavior.

## Phase 5 - Testing and hardening

Unit tests:

- Adapter parameter list contains `wblmaxmin`.
- Tile spec for `wblmaxmin` is non-wind indexed-fill path.
- Legend load path for `wblmaxmin` handled.
- Point-value fallback behavior for missing convergence field is non-fatal.

Regression checks:

- Existing thermal/rain/wind parameters unchanged.
- Wind display modes unaffected.
- No Track Up/North Up regressions from convergence changes.

Files:

- `feature/map/src/test/java/com/trust3/xcpro/forecast/SkySightForecastProviderAdapterTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/forecast/ForecastOverlayRepositoryTest.kt`

## Phase 6 - Verification

Run:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When device/emulator available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## Acceptance Criteria

- Convergence selectable in forecast parameter UI.
- Convergence tiles render on map.
- Convergence legend loads.
- Convergence point query does not break overlay rendering when field missing.
- Existing parameters (thermal, thermal height, rain, wind, cloud) still work.
- Architecture checks pass with no new deviations.

## Risks and Mitigations

Risk:

- Unknown convergence point-value field.

Mitigation:

- Ship convergence overlay with non-fatal point-value fallback.
- Close gap after evidence capture:
  - `convergence_tile_success.txt`
  - `convergence_legend_success.json`
  - `convergence_value_probe.json`

## Rollback

If regression appears:

1. Remove `wblmaxmin` from parameter catalog.
2. Keep existing forecast overlay behavior unchanged.
3. Re-run verification commands.
