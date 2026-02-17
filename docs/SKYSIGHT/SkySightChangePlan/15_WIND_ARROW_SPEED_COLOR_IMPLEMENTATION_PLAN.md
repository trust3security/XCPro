# Wind Arrow Speed Color Implementation Plan

Date: 2026-02-17
Owner: XCPro Team
Status: Implemented (2026-02-17)

## Purpose

Color only forecast wind arrows by wind speed using the active legend ramp, while keeping barbs unchanged.

Pilot intent:
- Arrow rotation conveys direction.
- Arrow color conveys speed by matching legend colors shown in the forecast sheet.
- Barbs keep current monochrome rendering.

Read first:

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`
6. `docs/SKYSIGHT/SkySightChangePlan/13_WIND_DISPLAY_MODES_IMPLEMENTATION_PLAN.md`

## 0) Metadata

- Title: Wind Arrow Speed Color
- Issue/PR: TBD
- Related docs:
  - `docs/integrations/skysight/evidence/legend_success.json`
  - `docs/SKYSIGHT/SkySightChangePlan/13_WIND_DISPLAY_MODES_IMPLEMENTATION_PLAN.md`

## 0.1 Current State (Codebase)

- Wind points are rendered in `ForecastRasterOverlay` for `VECTOR_WIND_POINTS`.
- Arrow mode uses one black bitmap icon and one symbol layer:
  - `WIND_ARROW_ICON_ID`
  - `WIND_GLYPH_COLOR_BLACK`
- Fill overlays already consume legend color stops through `buildIndexedColorExpression(...)`.
- Wind legend is already fetched and passed through runtime (`ForecastOverlayRepository` -> `MapScreenContent` -> `MapOverlayManager` -> `ForecastRasterOverlay`).
- Barbs are rendered via precomputed bucket icons and must remain unchanged.

## 0.2 Strategy Decision

Two valid approaches exist:

1. SDF + `iconColor(expression)`:
   - Pros: smooth interpolation across legend stops.
   - Risk: runtime icon SDF behavior and tint fidelity must be validated per-device.

2. Pre-colored arrow icon buckets + speed-step icon selection:
   - Pros: deterministic visual output, minimal SDK-specific risk.
   - Tradeoff: bucketed colors instead of continuous interpolation.

Planned default:
- Implement approach 2 first (safe baseline).
- Keep code structure ready for optional approach 1 follow-up.

## 1) Scope

- Problem statement:
  - Wind arrows are always black, so speed is not visually linked to legend colors.
- Why now:
  - Forecast wind direction is visible, but speed-at-a-glance remains weak in flight.
- In scope:
  - Arrow-only speed color mapping in map runtime.
  - Legend-aligned speed buckets.
  - Fallback behavior when legend is missing.
  - No behavior change for barbs.
- Out of scope:
  - Changing wind parameter APIs or provider contract.
  - Changing barb style/color.
  - New settings toggle.
- User-visible impact:
  - In arrow mode, colors represent wind speed and match the legend scale.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active legend stops | `ForecastOverlayRepository` | `ForecastOverlayUiState.legend` | Runtime-only authored legend ramps |
| Wind display mode | `ForecastPreferencesRepository` | `ForecastOverlayUiState.windDisplayMode` | Local UI/runtime display mode mirrors as authority |
| Arrow color decision at render time | `ForecastRasterOverlay` runtime | Layer properties only | ViewModel/domain-side color policy |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/usecase -> data`

Planned touched files:
- `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
- `feature/map/src/test/java/com/example/xcpro/map/...` (new helper tests)

Boundary risk:
- Must keep all MapLibre style/image/property operations in runtime map layer.
- No business/domain policy moves into ViewModel.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Arrow color mapping by speed | hardcoded black in runtime | runtime expression/icon mapping in runtime | render concern only | runtime manual checks + unit tests for mapping helpers |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | no new bypass expected | N/A | N/A |

### 2.3 Time Base

No new time-dependent logic.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Arrow speed color | N/A | driven by feature property `spd`, not clock |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Runtime style/image updates remain on map/UI runtime path.
- Cadence:
  - No new polling loops.
  - Recoloring occurs on existing overlay re-render triggers.
- Hot path budget:
  - No per-frame allocations beyond existing layer updates.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules: unchanged, visual-only overlay branch.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Runtime logic leaking to ViewModel/domain | ARCHITECTURE dependency direction | review + enforceRules | `ForecastRasterOverlay.kt` only |
| Arrow/barb regression on mode switch | map runtime stability | manual + test helper coverage | renderer helper tests + device check |
| Legend-missing crash | error handling rules | unit/manual fallback checks | helper tests + manual offline/failed legend scenario |

## 3) Data Flow (Before -> After)

Before:

`legend + spd -> ForecastRasterOverlay (arrow icon fixed black)`

After:

`legend + spd -> ForecastRasterOverlay (arrow icon/color selected by speed bucket aligned to legend stops)`

Barbs path stays:

`spd + dir -> existing barb bucket icons (unchanged)`

## 4) Implementation Phases

### Phase 0 - Contract Lock

- Goal:
  - Confirm arrow-only scope and fallback semantics.
- Files:
  - this plan doc.
- Exit criteria:
  - Decision recorded: no barb color changes.

### Phase 1 - Runtime Mapping Helpers

- Goal:
  - Add pure helper logic to convert legend stops into speed-color mapping suitable for arrows.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
- Changes:
  - Add helper(s) for legend stop normalization and fallback.
  - Ensure robust handling for empty/invalid stop lists.
- Tests:
  - helper tests for sorted stops, fallback, and bucket boundaries.
- Exit criteria:
  - helpers deterministic and covered by unit tests.

### Phase 2 - Arrow Rendering Integration

- Goal:
  - Apply speed-color mapping in arrow mode only.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
- Changes:
  - Update arrow image/property setup to use speed-color mapping.
  - Keep barb branch untouched.
  - Keep existing size/rotation behavior untouched.
- Tests:
  - update/add renderer helper tests.
- Exit criteria:
  - arrow mode colored by speed; barbs unchanged.

### Phase 3 - Fallback and Safety

- Goal:
  - Guarantee graceful rendering when legend is absent or stale.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
- Changes:
  - Explicit fallback to black arrows when legend unavailable.
  - Preserve map stability on style reload and overlay mode changes.
- Tests:
  - fallback scenario tests.
- Exit criteria:
  - no crash, no missing arrows on legend failures.

### Phase 4 - Verification

- Goal:
  - Validate architecture and runtime behavior end-to-end.
- Checks:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
  - Optional device validation in both wind parameters:
    - `sfcwind0`
    - `bltopwind`
- Exit criteria:
  - checks pass and manual visual acceptance met.

## 5) Test Plan

- Unit tests:
  - Legend stop normalization and ordering.
  - Speed-to-color bucket mapping boundaries.
  - Missing-legend fallback path.
- Replay/regression:
  - confirm no replay pipeline changes.
- UI/instrumentation:
  - optional visual smoke only (runtime style behavior).
- Failure-mode:
  - legend null/error should keep arrows visible (black fallback), no crash.

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
| Legend scale mismatch vs wind speed property units | misleading colors | verify against wind parameters (`kt`) and known samples | XCPro Team |
| Visual clutter from too many color steps | low readability | start with bounded buckets derived from legend stops | XCPro Team |
| Style reload losing arrow color setup | inconsistent rendering | reapply mapping in existing re-render path | XCPro Team |
| SDK tint/image limitations | inconsistent behavior across devices | default to pre-colored icon bucket approach | XCPro Team |

## 7) Acceptance Gates

- Arrow mode color follows speed and aligns with legend ordering.
- Barb mode unchanged.
- No architecture rule violations.
- No duplicate SSOT ownership introduced.
- Required checks pass.

## 8) Rollback Plan

- Revert arrow color mapping changes in `ForecastRasterOverlay`.
- Keep existing black arrow path.
- Preserve all non-color forecast behavior.
