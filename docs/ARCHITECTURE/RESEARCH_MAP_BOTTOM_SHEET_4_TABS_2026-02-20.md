# RESEARCH_MAP_BOTTOM_SHEET_4_TABS_2026-02-20.md

## Purpose

Capture implementation options for adding a compact 4-tab bottom sheet to MapScreen.
This is research only (no code changes in this document).

Date: 2026-02-20

## Requirement Summary

- Add a bottom sheet with 4 tabs.
- Tapping a tab opens the sheet.
- Each tab will later host different settings/options.
- Tabs should be discrete (separate buttons, not a large full-height control).
- Tabs should occupy very little vertical space at the bottom of the screen.
- Tab naming/order update:
  - Tab 1: `Weather`
  - Tab 2: `SkySight`
- `SkySight` tab options:
  - `Thermal Tops`
  - `Convergence`
  - `Satellite View`

## Confirmed Defaults (This Iteration)

Locked defaults applied to plan:

1. `1B` Weather write path:
   - use map-scoped weather-tab facade/ViewModel delegating to shared weather settings contracts.
2. `2A` Weather MVP scope:
   - enable switch,
   - opacity slider,
   - source attribution link action,
   - `More Weather Settings` drawer action.
3. `3B` SkySight `Satellite View`:
   - disabled/coming-soon in this slice (no map-style mutation path).
4. Modal presentation:
   - continue with `ModalBottomSheet` host behavior for this slice (no `BottomSheetScaffold` migration).
5. Drawer-open behavior for `More Weather Settings`:
   - explicit open semantics preferred over toggle semantics.
6. Tab-surface policy in modal flow:
   - closed state uses compact launcher tabs,
   - open state uses compact in-sheet footer tabs,
   - both share one selected-tab owner.
7. Drawer-blocked fallback:
   - if task-edit mode blocks drawer open, `More Weather Settings` should be disabled with clear reason text.
8. Drawer-first weather-route discoverability:
   - keep explicit helper path copy in tab UX:
     - `Settings -> General -> RainViewer`,
   - and expand navdrawer `Settings` section before/while opening drawer when persisted collapsed.

## Current Codebase Findings (Map Screen)

### Existing sheet patterns

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - Owns local `showForecastSheet` state.
  - Opens `ForecastOverlayBottomSheet`.
  - Also hosts OGN/thermal/ADS-B details modal sheets.
  - Forecast modal and details modal composition paths are independent today.

- `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
  - Uses `ModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
  - Is self-contained as a full modal container (content is not currently separable from its sheet wrapper).

- `feature/map/src/main/java/com/example/xcpro/map/MapTaskScreenManager.kt`
  - Already owns a separate task panel state machine (top panel, not bottom).

### Existing trigger patterns

- `feature/map/src/main/java/com/example/xcpro/map/components/MapActionButtons.kt`
  - FAB stack currently toggles traffic, circles, and opens forecast sheet.

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentOverlays.kt`
  - Good integration point for adding a new bottom tab-strip layer.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `onHamburgerTap` currently emits `MapUiEvent.ToggleDrawer` (toggle semantics, not explicit open).
- `feature/map/src/main/java/com/example/xcpro/navdrawer/NavigationDrawer.kt`
  - `gesturesEnabled = false`; drawer open on map route is programmatic.

### Back and layering behavior

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`
  - Back handling order today: drawer -> modal manager -> task panel -> nav back.
  - New sheet must either self-handle back (ModalBottomSheet) or be added to this order.
- `feature/map/src/main/java/com/example/xcpro/map/MapModalManager.kt`
  - Airspace modal overlay renders at high z-index and consumes taps.
  - `isAnyModalOpen()` reflects airspace modal only, not modal bottom sheets.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenContract.kt`
  - UI effects expose `OpenDrawer`/`CloseDrawer`, but UI events currently expose `ToggleDrawer` and `SetDrawerOpen` only.
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUiEventHandler.kt`
  - `setDrawerOpen(true)` mutates UI state but does not emit `OpenDrawer` effect; explicit-open semantics require separate wiring.
- `feature/map/src/main/java/com/example/xcpro/map/ui/task/MapTaskScreenUi.kt`
  - Task top panel uses high z-index and has its own expansion state.

### Current z-index occupancy (deep pass)

- Map gesture overlay path: around `zIndex(3f)` and `zIndex(3.6f)` in `MapOverlayStack`.
- Map widgets (hamburger, mode, ballast): around `zIndex(12f)`.
- Task top panel: `zIndex(70f)`; minimized indicator: `zIndex(71f)`.
- Airspace modal overlay: `zIndex(80f)` inside map overlay stack.
- Action buttons: mostly `zIndex(50f)` with badge surfaces at `zIndex(60f)`.

Implication:
- Bottom tab strip needs explicit draw/input priority and must not accidentally fall behind gesture overlays.

### Important implementation constraints

- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` is at line-budget limit (350).
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt` is near limit (320 max).
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt` is near/over tight budget.
- Best approach is new focused UI files + minimal callsite deltas.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - Already very large; integration should be done via extracted layer composables.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - Has existing drawer-open callback path (`onHamburgerTap`) that can support `2A` Weather MVP `More Weather Settings` action without adding new nav callback plumbing.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - Bottom-start debug panels and bottom-end replay/demo buttons already occupy lower screen space.
- No `feature/map/src/androidTest` folder currently exists; map UI interaction coverage is primarily JVM + Robolectric compose tests.
- Bottom overlays currently do not apply explicit navigation-bar insets in map UI code.
- Existing bottom-end lane can be occupied by:
  - AAT edit FAB,
  - demo/replay FAB stack,
  - optional debug controls in DEBUG sessions.

## Deep-Pass Gaps Found After Second Review

1. Modal-surface arbitration was underspecified.
   - Existing details sheets (OGN/thermal/ADS-B), forecast modal, and new tabbed sheet can conflict if not explicitly coordinated.

2. Task-panel coexistence was underspecified.
   - Task panel state and new bottom sheet need deterministic visibility rules to avoid dual control surfaces.

3. Navigation bar inset handling was missing.
   - New compact bottom tabs can be occluded by gesture/nav bars without explicit inset padding.

4. Forecast migration complexity was understated.
   - `ForecastOverlayBottomSheet` cannot simply be embedded into another sheet; content extraction or staged coexistence is needed.

5. File-budget risk mitigation needed tighter constraints.
   - Plan must avoid expanding `MapScreenViewModel` and `MapScreenScaffoldInputs`.
   - New logic should live in new map UI files and state helpers.

6. Test strategy needed to reflect repository reality.
   - Prefer Robolectric compose tests + pure state tests over non-existent feature-map instrumented tests.

## Deep-Pass Gaps Found After Third Review

1. Layering contract still needed explicit numeric targets.
   - Plan needs clear z-index policy for tabs/sheet host to avoid click-through into map gestures.

2. Bottom-lane collision policy needed explicit design.
   - New tab strip can collide with bottom-end replay/AAT FABs and bottom-start debug panels without lane reservation.

3. Nested modal-sheet prohibition needed to be explicit.
   - Forecast sheet is currently a full `ModalBottomSheet`; migration must avoid nesting one sheet inside another.

4. Back behavior with active task panel needed stronger verification.
   - Root `BackHandler` is enabled whenever task panel is visible; tabbed-sheet dismissal order must be explicitly tested.

5. Click-through prevention needed dedicated validation.
   - Tapping/dismissing sheet should not trigger underlying map tap/long-press handlers.

## Deep-Pass Gaps Found After Fourth Review

1. SkySight option mapping was partially incorrect in plan.
   - `Thermal Tops` and `Convergence` map to existing forecast parameter IDs.
   - `Satellite View` does not exist in current `SkySightForecastProviderAdapter` parameter list.

2. Label-vs-ID drift risk was not documented.
   - Backend parameters should be matched by stable IDs, not display names.
   - `Thermal Tops` must be treated as a UI alias for existing parameter `dwcrit` (`Thermal Height` in current metadata).

3. Availability fallback policy for missing parameters was missing.
   - If SkySight catalog omits an expected parameter, option should be disabled and not crash/force invalid selection.

4. Selection semantics mismatch was not documented.
   - Existing forecast primary-parameter model is not a free-form on/off set.
   - Current use-case semantics keep at least one primary selected; treat options as selector chips, not independent boolean switches.

## Deep-Pass Gaps Found After Fifth Review

1. SkySight tab interaction is blocked when primary forecast overlays are disabled.
   - In current forecast UI, primary chips are disabled unless `uiState.enabled == true`.
   - A new SkySight tab showing `Thermal Tops`/`Convergence` needs an explicit enable policy (toggle or auto-enable on first selection), otherwise options can appear "dead."

2. `Satellite View` map-style strategy has hidden wiring complexity.
   - Current map style updates are routed via map ViewModel and scaffold callback plumbing.
   - A tab-hosted map-style toggle requires both runtime style apply and persistence handling, plus previous-style restore behavior.

3. Forecast parity risk was understated for migration.
   - Existing forecast sheet includes wind/time/opacity/legend controls.
   - Replacing the forecast FAB path too early with a minimal SkySight tab would remove existing capabilities.

4. Layering guidance needed composition-order specificity.
   - `zIndex` alone is not enough across different composition subtrees.
   - Insertion order in `MapScreenContent` root `Box` must be specified so tab strip/sheet host does not render under or above wrong surfaces.

5. Compact-tab UX needs explicit touch-target policy.
   - Visual compactness requirement conflicts with tappability on small screens.
   - Plan should keep compact visuals but preserve reliable interaction target size.

## Deep-Pass Gaps Found After Sixth Review

1. Weather tab write ownership was underspecified.
   - `WeatherOverlayViewModel` is read-only (`overlayState` only).
   - Weather preference mutations currently live under navdrawer settings (`WeatherSettingsViewModel`/`WeatherSettingsUseCase`).
   - Plan must choose one mutation owner path for map tab content and forbid local duplicate mirrors.

2. Weather tab scope/parity gate was missing.
   - Existing weather settings include many controls (enable, opacity, animation window/speed, transition quality, frame mode/manual frame, render options).
   - Plan must define initial Weather-tab MVP controls and keep access to full settings until parity is explicitly approved.

3. Weather attribution/compliance handling was missing from tab plan.
   - Existing weather settings expose a source attribution section and external link action.
   - If Weather controls move into tabbed sheet, attribution access must remain explicit and test-covered.

4. Dual-entry consistency risk was not fully called out.
   - Weather tab and navdrawer weather screen both target the same preferences store.
   - Plan needs a contract for expected parity/sync behavior so users do not see divergent state across entrypoints.

## Deep-Pass Gaps Found After Seventh Review

1. `2A` advanced-settings entry path needed concrete MVP wiring.
   - `MapScreenContent` does not currently expose a direct weather-route callback.
   - `MapScreenScaffoldInputs` is near line-budget limits, so adding new route callback plumbing in MVP is risky.
   - Plan should use drawer-open action for `More Weather Settings` in this slice.

2. Weather settings contract placement risk was understated.
   - `WeatherSettingsUseCase` currently lives under navdrawer package ownership.
   - Reusing it directly from map tab would couple map tab UI to navdrawer internals.
   - Plan should move/expose shared weather settings contract under weather package ownership.

3. Drawer/sheet sequencing rule was missing.
   - If `More Weather Settings` opens drawer while tab sheet is still visible, modal overlap/back behavior can become ambiguous.
   - Plan should enforce: dismiss sheet first, then open drawer.

4. `3B` deferred satellite requires explicit no-side-effect assertion.
   - Disabled `Satellite View` must not trigger map-style apply/persist callbacks.
   - Plan should include tests for no map-style mutation in this slice.

## Deep-Pass Gaps Found After Eighth Review

1. Drawer-open path for `2A` advanced settings is currently toggle-based.
   - `MapScreenScaffoldInputs` routes hamburger tap to `MapUiEvent.ToggleDrawer`.
   - `ToggleDrawer` depends on mirrored drawer state and can theoretically close instead of open if state drifts.
   - Plan should define deterministic drawer-open behavior for `More Weather Settings` action.

2. Single-modal arbitration needed to be explicit for modal strategy.
   - Current map surface can compose multiple `ModalBottomSheet` callsites (forecast, OGN/thermal/ADS-B details).
   - New tabbed modal host must enforce one active modal sheet at a time.

3. Modal style lock needed explicit mention in plan contracts.
   - With modal decision confirmed, this slice should explicitly avoid `BottomSheetScaffold`/peek-sheet refactor.

4. Partial-expansion policy should be explicit for tabbed modal host.
   - Existing forecast modal uses `skipPartiallyExpanded = true`.
   - Plan should lock whether tabbed modal opens full-only vs partial for V1 to avoid UX drift.

## Deep-Pass Gaps Found After Ninth Review

1. Existing forecast + detail modal overlap risk needs explicit baseline callout.
   - `MapScreenContent` composes details sheets (`selectedOgnTarget`/`selectedOgnThermal`/`selectedAdsbTarget`) and forecast sheet (`showForecastSheet`) via independent conditions.
   - This can produce two `ModalBottomSheet` surfaces concurrently without explicit arbitration.

2. Modal arbitration cannot rely on `MapModalManager` visibility helpers.
   - `MapModalManager.isAnyModalOpen()` tracks airspace overlay only.
   - Plan must define modal-sheet arbitration in map UI state/host, independent of `MapModalManager`.

3. Modal expansion consistency should include existing details sheets.
   - Existing details sheets are not uniform (`OgnThermalDetailsSheet` allows partial, others default behavior, forecast is full-only).
   - Plan should explicitly define tabbed-modal expansion policy and whether detail sheets should remain mixed or be normalized later.

4. `2A` drawer-first advanced-settings flow needs explicit UX expectation.
   - `More Weather Settings` opens drawer, but does not deep-link directly to weather route in this slice.
   - Plan should mark this as intentional V1 behavior and test that transition remains predictable.

## Deep-Pass Gaps Found After Tenth Review

1. Modal-open tab accessibility was underspecified.
   - With full-open modal policy, outside launcher tabs can be obscured by sheet/scrim.
   - Requirement asks for discrete tabs occupying a very small bottom sheet area.
   - Plan should include compact in-sheet footer tabs while modal is visible.

2. Shared tab SSOT across closed/open states was missing.
   - Closed state uses launcher tabs; open state needs in-sheet footer tabs.
   - Both surfaces must bind to one selected-tab owner to avoid drift.

3. Legacy forecast path still needs explicit task-panel gating.
   - Forecast FAB can open modal while task panel is expanded unless guarded.
   - Plan should enforce consistent panel/modal gating across both legacy and new entry paths.

## Deep-Pass Gaps Found After Eleventh Review

1. Legacy forecast/task-panel/back precedence conflict is concrete in current wiring.
   - `MapScreenContent` still opens forecast modal via local `showForecastSheet` state from action buttons path.
   - `MapScreenRoot` back handling is enabled whenever task panel is non-hidden.
   - If forecast opens while task panel is visible, back handling precedence can favor task panel dismissal first.

2. Explicit drawer-open semantics are not currently available as a dedicated UI event.
   - `MapUiEvent` exposes `ToggleDrawer` and `SetDrawerOpen`, but not explicit `OpenDrawer`.
   - `MapScreenUiEventHandler.setDrawerOpen(...)` updates state only and does not emit `MapUiEffect.OpenDrawer`.
   - A "deterministic open" plan must either add explicit open command wiring or pass a direct open callback.

3. Drawer-blocking task mode can make Weather advanced action appear broken.
   - `MapScreenRuntimeEffects` closes/blocks drawer when AAT edit mode requires drawer blocking.
   - `More Weather Settings` drawer-first behavior needs explicit blocked-state UX (disable + reason) to avoid no-op perception.

4. Dual-surface tabs requirement still needed plan-level hardening.
   - Full-open modal can obscure outside launcher tabs.
   - To keep discrete tab switching visible while open, sheet content needs compact in-sheet footer tabs bound to same selected-tab SSOT.

## Deep-Pass Gaps Found After Twelfth Review

1. Drawer-first Weather advanced path discoverability was still underspecified.
   - Current V1 flow is not a one-step route; it is:
     - open drawer -> `Settings`/`General` -> `RainViewer`.
   - Plan needed explicit helper-copy contract so this path is understandable in-tab.

2. Label mismatch risk was not explicitly encoded.
   - Bottom tab label is `Weather`, while current settings card label is `RainViewer`.
   - Without explicit copy, users can miss the weather destination after drawer opens.

3. Persisted collapsed drawer section can hide entry point.
   - `settingsExpanded` is persisted and can be false from prior user state.
   - Drawer-first flow should expand `Settings` section before/while opening drawer to keep path discoverable.

4. Discoverability test coverage was incomplete.
   - Existing plan checks drawer-first behavior generally.
   - Needed explicit checks for `Weather`->`General`->`RainViewer` discoverability and collapsed-section start state.

## Weather Settings Ownership and Scope (Verified in Code)

- Read path in map:
  - `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherOverlayViewModel.kt`
  - Exposes runtime `overlayState` only.
  - Applied to map rendering by:
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapWeatherOverlayEffects.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`

- Write path today:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsUseCase.kt`
  - Mutates `WeatherOverlayPreferencesRepository` for weather settings.
  - Current mutation contract lives under navdrawer package ownership, which is a coupling risk for map-tab reuse.

- Existing full weather controls + attribution UI:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
  - Includes radar metadata/status details and source attribution link action.

- Existing weather settings entrypoint remains active:
  - `feature/map/src/main/java/com/example/xcpro/navigation/SettingsRoutes.kt`
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`

## Weather Advanced-Settings Discoverability (Verified in Code)

- Drawer-open trigger from map flow:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `onHamburgerTap = { mapViewModel.onEvent(MapUiEvent.ToggleDrawer) }`
  - No direct `weather_settings` deep-link callback on this path today.

- Drawer interaction model on map:
  - `feature/map/src/main/java/com/example/xcpro/navdrawer/NavigationDrawer.kt`
  - `gesturesEnabled = false` (drawer opens programmatically).

- Drawer route to settings:
  - `feature/map/src/main/java/com/example/xcpro/navdrawer/DrawerMenuSections.kt`
  - `Settings` section contains `General` item that navigates to `settings`.

- Settings route to weather screen:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt`
  - card label is `RainViewer`, and that card navigates to `SettingsRoutes.WEATHER_SETTINGS`.
  - This is the current label users must recognize from a Weather-tab advanced action.

- Weather settings route target:
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  - `SettingsRoutes.WEATHER_SETTINGS` maps to `WeatherSettingsScreen`.

- Persisted discoverability variable:
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  - `settingsExpanded` is restored from config and can be user-collapsed.

## SkySight Tab Option Mapping (Verified in Code)

Verified in `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt`:

- `Thermal Tops` (UI label) -> `ForecastParameterId("dwcrit")`
  - Current metadata label is `Thermal Height`.
- `Convergence` -> `ForecastParameterId("wblmaxmin")`
  - Current metadata label is `Convergence`.
- `Satellite View`:
  - Not present as a forecast parameter ID in current SkySight forecast adapter.
  - Requires one of:
    - map-style satellite toggle path (existing base-map style), or
    - dedicated satellite overlay implementation track (see `docs/SKYSIGHT/SkySightChangePlan/18_SATELLITE_OVERLAY_IMPLEMENTATION_PLAN.md`).

Selection model constraints from current forecast use-cases:

- Primary overlays support up to two selected parameters (primary + optional secondary).
- A completely empty primary selection is not the normal path.
- SkySight-tab UI should avoid representing Thermal Tops/Convergence as three-state independent toggles.

Enabled-state constraint from current forecast sheet:

- Primary parameter chips are disabled when primary overlays are disabled (`uiState.enabled == false`).
- SkySight tab must include:
  - an explicit primary-overlay enable control, or
  - an auto-enable behavior before applying parameter selection.

## Satellite Map-Style Path Constraints (Verified in Code)

- Valid map style tokens currently used in UI:
  - `Topo`
  - `Satellite`
  - `Terrain`
- Runtime map style apply path:
  - `MapScreenViewModel.setMapStyle(styleName)` -> emits `MapCommand.SetStyle`.
- Persistence path:
  - `MapScreenViewModel.persistMapStyle(styleName)` via `MapStyleUseCase`/`MapStyleRepository`.
- Current map-style callback plumbing lives in scaffold/root wiring and is not directly available inside `MapScreenContent`.

Implication:

- If `Satellite View` uses map-style strategy in this slice, plan must include callback/state wiring and "restore previous non-satellite style" behavior.
- With locked default `3B`, this map-style bridge work is explicitly deferred out of scope.

## External API Research (Compose/Android)

- Compose bottom sheets overview:
  - https://developer.android.com/develop/ui/compose/components/bottom-sheets
- Partial modal bottom sheet behavior:
  - https://developer.android.com/develop/ui/compose/components/bottom-sheets-partial
- Material3 `BottomSheetScaffold` API reference:
  - https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary
- Compose tabs guidance:
  - https://developer.android.com/develop/ui/compose/components/tabs

## Options Considered

### Option A: Discrete bottom tab strip + `ModalBottomSheet` (Recommended)

How it works:
- Keep a tiny always-visible tab strip anchored at bottom.
- On tab tap, set selected tab and show modal bottom sheet.
- Sheet content swaps based on selected tab.

Pros:
- Lowest integration risk with current map architecture.
- No full-screen scaffold refactor.
- Easy to keep tabs visually discrete and compact.
- Aligns with existing `ModalBottomSheet` usage in map code.

Cons:
- Tab strip is not technically the "peek" area of the sheet component.
- Needs explicit coexistence rules with bottom-end debug/demo buttons.

### Option B: `BottomSheetScaffold` with tab strip as peek content

How it works:
- Use `BottomSheetScaffold`.
- Set a very small `sheetPeekHeight`.
- Put 4-tab row in sheet header; tap tab expands sheet.

Pros:
- Semantically perfect: tabs are literally part of collapsed sheet.
- Built-in collapsed/expanded sheet model.

Cons:
- Higher refactor risk in current `MapScreenContent` composition/layering.
- Higher chance of regressions in gestures, overlays, and existing map UI stacks.

### Option C: Custom draggable bottom panel (task-panel style clone)

How it works:
- Build custom panel with draggable height and tab area.

Pros:
- Full control over behavior/visuals.

Cons:
- Most code and test burden.
- Reimplements behavior already provided by Material3 sheet components.
- Higher maintenance risk.

## Recommendation

Choose **Option A** first.

Reason:
- Meets UX requirement quickly (small discrete tabs at bottom + tab opens sheet).
- Keeps architecture impact limited to UI layer.
- Avoids large refactors in files that are already near maintainability limits.
- Easy to evolve later when each tab gets real content.
- Allows phased migration so existing forecast behavior can remain stable until extracted.

## Proposed UX Baseline (for implementation phase)

- Bottom tab strip:
  - Height target: 36-44dp.
  - Keep a centered compact width envelope so bottom-end action lane remains usable.
  - 4 separate pill/segment buttons with icon + short label.
  - Compact horizontal spacing.

- Interaction:
  - Tap tab when sheet closed: open sheet to that tab.
  - Tap different tab while sheet open: switch content in place.
  - While sheet is open, use compact in-sheet footer tabs for switching (launcher strip may be obscured).
  - Tap same selected tab while sheet open: optional close toggle (confirm UX).

- Sheet:
  - Start with placeholder content blocks per tab.
  - V1 modal host opens full-only (`skipPartiallyExpanded = true`) for tabbed sheet.
  - Do not nest `ModalBottomSheet` composables; use a single sheet host for the tabbed flow.

## Open Decisions for Confirmation

- Final tab IDs/labels/icons for the initial 4 tabs
  - `Weather` is fixed as tab 1.
  - `SkySight` is fixed as tab 2.
- Whether tapping the active tab should close the sheet.
- Whether existing Forecast FAB should remain during rollout or be replaced by one tab.
- Conflict policy when task panel is expanded:
  - hide tabs, or keep tabs visible but disabled.
- Conflict policy when traffic details sheet is visible:
  - dismiss details then open tabs, or block tabs while details are visible.
- Locked defaults already chosen for this slice:
  - `1B` weather-tab facade ownership,
  - `2A` weather MVP scope,
  - `3B` deferred satellite option,
  - modal host strategy,
  - explicit drawer-open semantics for `More Weather Settings`,
  - drawer-first route discoverability helper (`Settings -> General -> RainViewer`) with collapsed-settings expansion policy.
