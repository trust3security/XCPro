# Map Screen Shell Phase 1 Seam Lock

## 0) Metadata

- Title: Phase 1 seam lock for MapScreen shell ownership extraction
- Owner: Codex
- Date: 2026-03-14
- Status: Draft
- Parent plan:
  - `docs/refactor/Map_Screen_Shell_Ownership_Extraction_Plan_2026-03-14.md`

## 1) Purpose

Lock the exact first implementation cut before production edits begin.

This seam-lock pass is intentionally narrow:

- Phase 1 only
- no broad code pass
- no root/scaffold/ViewModel changes
- no speculative new facade layer

## 2) Decision Summary

The first implementation package should keep the public `MapScreenContent(...)`
signature stable and make `MapScreenContentRuntime.kt` thin by moving internal
state ownership into focused state/helpers.

Use the current rendering boundaries that already exist:

- `MapScreenContentRuntimeSections.kt`
- `MapScreenContentOverlays.kt`
- `feature/traffic/.../MapTrafficRuntimeLayer.kt`

Do not create competing render hosts for concerns that already have a stable UI
section. Phase 1 should extract state and leaf-feature wiring first, then let
the existing sections render that state.

Professional constraint:

- If Phase 1 starts forcing changes in `MapScreenScaffoldInputs.kt`,
  `MapScreenScaffoldInputModel.kt`, `MapScreenScaffoldContentHost.kt`,
  `MapScreenBindings.kt`, or `MapScreenRoot.kt`, stop. That is Phase 2 or
  Phase 3 work, not Phase 1.

## 3) Exact State Inventory

Source file:

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`

| Lines | State / Collection | Current Owner | Phase 1 Owner | Notes |
|---|---|---|---|---|
| 173 | `liveFlightData` | `MapScreenContentRuntime.kt` | `rememberMapQnhUiState(...)` helper | used for current QNH label and dialog seeding |
| 174-181 | `forecastViewModel`, `forecastOverlayState`, `forecastPointCallout`, `forecastQueryStatus`, `weatherOverlayViewModel`, `weatherOverlayState`, forecast runtime warning, satellite runtime error | `MapScreenContentRuntime.kt` | `rememberMapScreenForecastWeatherState(...)` helper | keep forecast/weather collection together |
| 182-186 | `trafficRuntimeState`, `trafficContentUiState` | `MapScreenContentRuntime.kt` | traffic runtime seam already exists; use a small adapter/helper only if needed | do not redesign traffic in Phase 1 |
| 187-190 | `currentQnhLabel` | `MapScreenContentRuntime.kt` | `rememberMapQnhUiState(...)` helper | shared by task layer and bottom tabs |
| 192-194 | `showQnhDialog`, `qnhInput`, `qnhError` | `MapScreenContentRuntime.kt` | `rememberMapQnhUiState(...)` helper | UI-only state |
| 195-197 | `selectedBottomTabName`, `isBottomTabsSheetVisible`, `lastNonSatelliteMapStyleName` | `MapScreenContentRuntime.kt` | `rememberMapBottomTabsUiState(...)` helper | UI-only state |
| 198-200 | `tappedWindArrowCallout`, `windTapLabelSize`, `overlayViewportSize` | `MapScreenContentRuntime.kt` | `rememberMapWindTapUiState(...)` helper | UI-only geometry/display state |
| 201 | `selectedBottomTab` | `MapScreenContentRuntime.kt` | `rememberMapBottomTabsUiState(...)` helper | derived from selected tab key |
| 202-203 | `taskPanelState`, `isTaskPanelVisible` | `MapScreenContentRuntime.kt` | `rememberMapBottomTabsUiState(...)` helper | needed only for bottom-sheet suppression |
| 204-212 | SkySight warning/error resolution | `MapScreenContentRuntime.kt` | `rememberMapScreenForecastWeatherState(...)` helper | keep warning policy near forecast/weather collection |
| 215-218 | `isForecastWindArrowOverlayActive`, `skySightSatViewEnabled` | `MapScreenContentRuntime.kt` | split: wind-arrow active flag to wind helper, satellite view flag to forecast/bottom-tabs models | do not duplicate state |
| 219-253 | `openQnhDialog`, `MapQnhDialogInputs` assembly | `MapScreenContentRuntime.kt` | `rememberMapQnhUiState(...)` helper | reuse `MapQnhDialogInputs` model |
| 254-275 | `MapAuxiliaryPanelsInputs` assembly | `MapScreenContentRuntime.kt` | content runtime remains assembler only | inputs are populated from extracted helpers |
| 276-280 | `LaunchedEffect(currentMapStyleName)` updating `lastNonSatelliteMapStyleName` | `MapScreenContentRuntime.kt` | `rememberMapBottomTabsUiState(...)` helper | state owner belongs with bottom-tab/satellite toggle logic |
| 281-286 | `ForecastOverlayRuntimeEffects(...)` | `MapScreenContentRuntime.kt` | forecast/weather helper or retained content runtime call with grouped forecast state | effect belongs to forecast/weather concern |
| 287-292 | `WindArrowTapRuntimeEffects(...)` | `MapScreenContentRuntime.kt` | `rememberMapWindTapUiState(...)` helper or wind helper file | clear/reset effect belongs with wind-tap state |
| 293-297 | bottom-sheet suppression effect | `MapScreenContentRuntime.kt` | `rememberMapBottomTabsUiState(...)` helper | driven by task panel and traffic details visibility |
| 299-305 | `overlayViewportSize` update from root `Box` size | `MapScreenContentRuntime.kt` | `rememberMapWindTapUiState(...)` helper | still wired from content root, but state owner moves |

## 4) Phase 1 Ownership Targets

### 4.1 QNH Slice

Owner target:

- new helper: `rememberMapQnhUiState(...)`

Owns:

- `liveFlightData` collection
- `currentQnhLabel` formatting
- dialog visibility/input/error state
- `MapQnhDialogInputs` assembly
- dialog open/confirm/invalid/dismiss handlers

Must not own:

- QNH business rules
- QNH persistence
- forecast/weather state

Reuse:

- existing `MapQnhDialogInputs`
- existing `QnhDialogHost(...)` in `MapScreenContentOverlays.kt`

### 4.2 Forecast / Weather Slice

Owner target:

- new helper: `rememberMapScreenForecastWeatherState(...)`

Owns:

- forecast/weather Hilt view model collection
- SkySight warnings/errors resolution
- forecast point callout and query-status adapter state
- forecast overlay runtime effect inputs
- wind overlay active flag

Must not own:

- bottom-tab selected state
- QNH state
- traffic details state

### 4.3 Bottom Tabs Slice

Owner target:

- new helper: `rememberMapBottomTabsUiState(...)`

Owns:

- selected tab key and resolved tab
- sheet visibility
- last non-satellite style memory
- suppression when task panel or traffic details are open

Consumes:

- `hasTrafficDetailsOpen` from traffic seam
- `isTaskPanelVisible` from task panel state
- forecast/satellite inputs needed for the SkySight tab

Must not own:

- forecast/weather authoritative state
- traffic runtime state
- QNH dialog state

### 4.4 Wind Tap Slice

Owner target:

- new helper: `rememberMapWindTapUiState(...)`

Owns:

- tapped callout
- label size
- viewport size
- clear/reset effects tied to wind overlay activity

Must not own:

- forecast state other than the already-derived "wind overlay active" flag

### 4.5 Traffic Slice

Owner target:

- keep existing traffic seam

Use as-is:

- `rememberMapTrafficRuntimeState(...)`
- `MapTrafficRuntimeLayer(...)`
- `MapTrafficContentUiState`

Phase 1 rule:

- do not create a new `MapScreenTrafficHost.kt` unless the remaining local
  adapter code in `MapScreenContentRuntime.kt` is still materially broad after
  the other extractions.

## 5) Concrete Phase 1 File Cut

### 5.1 Files To Modify

| File | Why It Changes | Ownership Change |
|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt` | reduce it to orchestration and render assembly | stops owning QNH, bottom-tab, wind-tap, and forecast/weather state directly |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenAuxiliaryPanelsInputs.kt` | may need grouped inputs refined for extracted helpers | remains a UI input-model file |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSections.kt` | may accept grouped helper outputs instead of raw local vars | remains render-section ownership only |

### 5.2 Files To Add

| File | Responsibility |
|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenQnhUiState.kt` | QNH live-data collection, dialog state, QNH label, and `MapQnhDialogInputs` assembly |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenForecastWeatherState.kt` | forecast/weather collection, warning/error resolution, runtime-effect inputs, and forecast UI adapters |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBottomTabsUiState.kt` | bottom-tab UI state, satellite-style restore memory, and bottom-sheet suppression |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenWindTapUiState.kt` | wind tap callout state, viewport/label geometry, and clear/reset effects |

### 5.3 Files Explicitly Not Touched In Phase 1

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldContentHost.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputModel.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSections.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`

If implementation pressure reaches any of those files, stop and re-scope. That
means the work is crossing into Phase 2 or Phase 3.

## 6) Concrete First PR Order

1. Extract `MapScreenQnhUiState.kt`.
2. Extract `MapScreenWindTapUiState.kt`.
3. Extract `MapScreenForecastWeatherState.kt`.
4. Extract `MapScreenBottomTabsUiState.kt`.
5. Slim `MapScreenContentRuntime.kt` to assemble the extracted helper outputs.

Reason for this order:

- QNH and wind-tap are local and low-risk.
- forecast/weather is broader but still self-contained.
- bottom tabs depend on task visibility, traffic details, and forecast inputs, so
  it should come after the other state helpers are stable.

## 7) Phase 1 Stop Rules

Stop and re-evaluate if any of these become necessary:

- changing the public `MapScreenContent(...)` parameter list
- changing scaffold/root/binding files
- moving business logic into composables
- introducing a new giant "Phase 1 state" model
- rewriting the traffic runtime path instead of reusing it

## 8) Smoke Checklist For Phase 0 / Phase 1

### Core

- Map renders and remains interactive on initial entry.
- Recenter and return buttons still work.
- Task panel still opens and closes normally.

### QNH

- Open QNH dialog from bottom tabs.
- Invalid input shows error.
- Valid input confirms and closes.
- Auto-calibrate still closes the dialog and routes correctly.

### Forecast / Weather

- Enable forecast overlay and confirm overlay still renders.
- Long-press forecast query still shows point callout.
- Query status chip still appears and dismisses.
- SkySight warning/error messaging still appears in the bottom tab path.

### Wind Tap

- With vector wind overlay active, tap wind arrow and confirm speed label appears.
- Label still clears automatically.
- Turning wind overlay off clears any active label.

### Bottom Tabs

- Bottom tabs still open and dismiss the sheet.
- Selecting a tab still dismisses traffic details if open.
- Opening task panel or traffic details still suppresses the bottom sheet.
- Satellite map-style toggle still restores the previous non-satellite style.

### Traffic

- Traffic details still open and dismiss.
- Trail aircraft toggles still work.
- Traffic panels/sheets still render.

### Replay / Prompt

- Replay action buttons remain wired as before.
- WeGlide prompt still appears and dismisses.

## 9) Implementation Notes

- Prefer extracted `remember...` helpers over new render hosts in Phase 1.
- Keep `MapScreenContentRuntime.kt` as the assembler until Phase 2 narrows the
  scaffold/content contract.
- Reuse existing section files before introducing new patterns.
- File creation in Phase 1 is justified only when it creates a clear state owner,
  not when it merely relocates rendering code.
