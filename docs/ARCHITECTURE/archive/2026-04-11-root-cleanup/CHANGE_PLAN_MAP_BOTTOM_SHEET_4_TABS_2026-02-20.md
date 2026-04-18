# CHANGE_PLAN_MAP_BOTTOM_SHEET_4_TABS_2026-02-20.md

## Purpose

Implementation plan for adding a compact 4-tab bottom sheet entry on MapScreen.
This plan is intentionally UI-layer scoped and preserves existing map/task/sensor pipelines.

Date: 2026-02-20
Status: Draft (defaults locked: 1B, 2A, 3B, 4A + modal host + discoverability + drawer-command + task-panel policy)

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/RESEARCH_MAP_BOTTOM_SHEET_4_TABS_2026-02-20.md`

## 0) Metadata

- Title: Map Screen Compact 4-Tab Bottom Sheet
- Owner: XCPro Team
- Date: 2026-02-20
- Issue/PR: TBD
- Status: Draft (defaults locked: 1B, 2A, 3B, 4A + modal host + discoverability + drawer-command + task-panel policy)

## 1) Scope

- Problem statement:
  MapScreen currently has several separate FAB/sheet entry points. We need one compact bottom tab strip with 4 discrete tabs that opens a bottom sheet on tab tap and can later host tab-specific options.

- Why now:
  Creates a scalable container for additional map controls without adding more floating controls.

- In scope:
  - Add a compact 4-tab bottom strip on MapScreen.
  - Add a modal bottom sheet host that opens from tab taps.
  - Add placeholder tab content areas for future options.
  - Define tab naming/order now:
    - Tab 1: `Weather`
    - Tab 2: `SkySight`
  - Define one concrete content tab now:
    - `SkySight` tab shows options:
      - `Thermal Tops`
      - `Convergence`
      - `Satellite View`
  - Define Weather-tab ownership contract before implementation:
    - map Weather tab must not introduce a second settings owner.
  - Define Weather-tab MVP scope/parity gate:
    - explicit initial control set vs full navdrawer weather settings parity.
  - Preserve Weather attribution access in the map tabbed flow.
  - Lock modal strategy for this slice:
    - `ModalBottomSheet` host, not `BottomSheetScaffold` migration.
  - Use dual tab surfaces with one owner:
    - compact launcher tabs when sheet is closed,
    - compact in-sheet footer tabs when sheet is open.
  - Add explicit modal/panel coexistence rules (task panel, traffic detail sheets, airspace modal).
  - Add explicit bottom inset handling so the tab strip is safe above system navigation bars.
  - Define deterministic behavior when `More Weather Settings` is invoked while drawer is blocked by task-edit mode.
  - Define deterministic V1 discoverability for drawer-first weather advanced path (`Settings -> General -> RainViewer`).
  - Define drawer-command separation so Weather advanced action does not depend on hamburger toggle semantics.
  - Define tabbed-sheet/task-panel arbitration using non-hidden panel semantics (not expanded-only wording).
  - Define async sequencing barrier for Weather advanced flow: open drawer only after sheet-dismiss completion.
  - Keep initial integration line-budget safe by isolating new logic in new UI files.

- Out of scope:
  - Sensor/fusion/replay/task domain changes.
  - New business logic for options inside tabs.
  - Navigation architecture changes.
  - Expanding `MapScreenViewModel` or `MapScreenScaffoldInputs` unless proven necessary.

### 1.0A Initial Tab Order Contract (Confirmed)

Required order for this implementation:

1. Tab 1 label is `Weather`.
2. Tab 2 label is `SkySight`.
3. Tab 3/4 labels remain TBD until confirmed.

### 1.0B Weather Tab Ownership Contract (Confirmed Default: 1B Facade)

Current behavior baseline:

- `WeatherOverlayViewModel` is runtime-read only (`overlayState`).
- Weather preference mutations currently live in navdrawer weather settings flow:
  - `WeatherSettingsViewModel` -> `WeatherSettingsUseCase` -> `WeatherOverlayPreferencesRepository`.

Confirmed default for this tab:

1. Use a map-scoped weather-tab facade/ViewModel (`1B`).
2. Facade delegates to shared weather settings contracts backed by `WeatherOverlayPreferencesRepository`.
3. Do not couple map tab UI directly to navdrawer-specific `WeatherSettingsViewModel`.

Policy constraints:

- Do not create a second weather preference owner.
- Do not keep long-lived local weather setting mirrors in Compose state.
- Runtime weather status/metadata reads remain on existing overlay-state flow owner.

### 1.0C Weather Tab Scope and Parity Gate (Confirmed Default: 2A MVP)

Required:

1. Initial Weather-tab MVP controls are:
   - Rain overlay enable switch.
   - Opacity slider.
   - Source attribution link action.
   - `More Weather Settings` action that opens the existing nav drawer path (no direct route callback in MVP slice).
2. Keep existing navdrawer weather settings route available until parity sign-off.
3. Any weather controls excluded from MVP remain reachable in navdrawer flow.
4. Weather-route consolidation/removal is allowed only after explicit parity checklist pass.
5. `More Weather Settings` action must dismiss tab sheet first, then open drawer.
6. `More Weather Settings` is drawer-first in V1 (no direct deep-link navigation to weather route in this slice).
7. Weather tab must include explicit helper copy for V1 route discoverability:
   - `Open drawer -> Settings -> General -> RainViewer`.
8. If navdrawer `Settings` section is persisted collapsed, `More Weather Settings` flow should expand that section before/while opening drawer.
9. Because tab label is `Weather` while current settings card label is `RainViewer`, helper copy must reference `RainViewer` explicitly.

### 1.0D Weather Attribution and Dual-Entry Contract

Required:

1. Weather tab must expose radar source attribution affordance.
2. Weather tab must provide link action equivalent to existing weather settings behavior.
3. If tab + navdrawer entrypoints coexist, both must stay consistent via shared SSOT-backed flows.

### 1.0E Locked Defaults Summary

This plan iteration locks these defaults:

1. `1B` Weather ownership: map-scoped facade/ViewModel delegating to shared weather settings contracts.
2. `2A` Weather MVP: enable, opacity, attribution link, and `More Weather Settings` drawer action.
3. `3B` Satellite View: disabled/coming-soon in SkySight tab for this slice.
4. `4A` Drawer-open determinism: use explicit open semantics for `More Weather Settings` (no ambiguous toggle path).
5. Drawer-blocked fallback in V1: if drawer is blocked by task-edit mode, `More Weather Settings` is disabled with explanatory copy (no hidden no-op).
6. Drawer-first discoverability fallback in V1: Weather tab helper copy explicitly calls out `Settings -> General -> RainViewer`.
7. Drawer-command separation fallback in V1: Weather advanced action uses dedicated explicit-open request, not hamburger toggle callback reuse.
8. Task-panel arbitration fallback in V1: treat panel non-hidden state (`COLLAPSED` or expanded) as blocking/conflicting for tabbed-sheet open paths.
9. Weather advanced sequencing fallback in V1: dismiss sheet completion barrier is required before drawer open command dispatch.

### 1.0F Modal Host Policy (Locked)

This slice uses modal-sheet behavior:

1. Use one shared `ModalBottomSheet` host for tabbed content.
2. Do not migrate to `BottomSheetScaffold`/peek-sheet pattern in this slice.
3. V1 open behavior is tab-tap driven; drag-up from a persistent peek state is not required.
4. Lock tabbed modal sheet to full open in V1 (`skipPartiallyExpanded = true`) unless explicitly changed.
5. Existing detail-sheet expansion behavior may remain mixed in V1; normalize only if explicitly scoped.

### 1.0G Drawer Open Determinism for `More Weather Settings` (Confirmed Default: 4A Explicit Open)

Required:

1. `More Weather Settings` triggers explicit drawer-open behavior (`4A`), not toggle behavior.
2. If implementation cannot avoid toggle path, treat it as deviation from default and prove deterministic-open preconditions with tests.
3. Sequence remains: dismiss tab sheet first, then open drawer.

### 1.0H Tab Surface and Drawer-Blocked Policy (Locked)

Required for this slice:

1. Closed state uses a compact external launcher tab strip.
2. Open sheet state uses compact in-sheet footer tabs for tab-to-tab switching.
3. Both tab surfaces bind to one selected-tab owner (no duplicated selected-tab state).
4. If drawer is blocked (for example AAT edit-mode guard), `More Weather Settings` must not silently fail:
   - disable action and show reason text in the tab content.
5. Because drawer gestures are globally disabled in current `NavigationDrawer`, block policy must guard explicit/programmatic drawer-open commands (not gesture paths only).

### 1.0I Drawer-First Settings Discoverability Contract (Locked)

Required for this slice:

1. Keep `More Weather Settings` drawer-first in V1 (no direct weather deep-link callback required).
2. Weather tab content must include route helper copy:
   - `Open drawer -> Settings -> General -> RainViewer`.
3. `More Weather Settings` action should ensure the navdrawer `Settings` section is expanded before/while opening drawer.
4. Weather-tab helper copy must acknowledge current `RainViewer` label to avoid Weather-vs-RainViewer ambiguity.
5. Discoverability behavior must be test-covered (manual + compose) and not rely on user memory of drawer hierarchy.

### 1.0J Drawer Command Separation and Expansion Persistence Contract (Locked)

Required for this slice:

1. `More Weather Settings` must use a dedicated explicit drawer-open request path; it must not call shared hamburger toggle callback directly.
2. Hamburger widget tap behavior remains toggle-based (no regression in existing close/open semantics for that control).
3. Drawer block predicate must guard any drawer-open vector, including:
   - dedicated weather advanced open request,
   - hamburger toggle when current drawer state is closed,
   - any future programmatic explicit-open effect path.
4. Discoverability-driven `Settings` section expansion is transient by default:
   - do not persist forced expansion as a user preference change unless user explicitly toggles the section.

### 1.0K Task-Panel Visibility and Sequencing Contract (Locked)

Required for this slice:

1. Tabbed-sheet/task-panel arbitration must use panel non-hidden semantics:
   - `COLLAPSED`, `EXPANDED_PARTIAL`, and `EXPANDED_FULL` are all treated as panel-visible for conflict policy.
2. Any contracts/tests that currently refer to "expanded only" must be interpreted as non-hidden unless explicitly scoped otherwise.
3. Weather advanced sequence must use an async completion barrier:
   - do not dispatch drawer-open command until tab sheet dismissal is completed.
4. If dismissal completion cannot be observed directly in this slice, define a deterministic one-shot callback barrier in host state helper and test it.

### 1.1 SkySight Tab Option Mapping (Required)

Use ID-driven mapping (not display-name string matching):

| SkySight Option Label | Backend Mapping | Current Availability | Notes |
|---|---|---|---|
| `Thermal Tops` | `ForecastParameterId("dwcrit")` | Available | UI label aliases provider label `Thermal Height` |
| `Convergence` | `ForecastParameterId("wblmaxmin")` | Available | Existing SkySight forecast parameter |
| `Satellite View` | No SkySight forecast parameter mapping | Not available in current forecast adapter | Must use approved fallback strategy (Section 1.2) |

SkySight selection semantics:

- `Thermal Tops` / `Convergence` must follow existing forecast primary-selection model:
  - up to two primary overlays selected,
  - avoid independent boolean-switch semantics that imply "none selected" is always valid.
- Recommended UI control: chips/buttons reflecting selected parameter IDs, not simple on/off toggles.

### 1.1A SkySight Activation Policy (Mandatory Decision)

Current behavior baseline:

- Primary-parameter controls are disabled when forecast primary overlays are disabled.

Required for this tab:

1. `Enable Toggle` strategy (recommended):
   - Add a compact `Show non-wind overlays` switch in `SkySight` tab.
   - Parameter options remain disabled until enabled.

2. `Auto Enable` strategy:
   - First tap on `Thermal Tops`/`Convergence` enables primary overlays and applies selection.

Policy constraints:

- Do not alter wind-overlay enabled state while applying this primary-overlay policy.
- Do not reset existing selected parameter IDs unless user explicitly changes them.

### 1.2 Satellite View Strategy (Confirmed Default: 3B Deferred)

Because `Satellite View` is not a current forecast parameter, this slice uses `3B`:

1. `Satellite View` is rendered disabled/coming-soon in `SkySight` tab.
2. No map-style mutation is performed from this control in this slice.
3. Track full implementation under:
   - `docs/SKYSIGHT/SkySightChangePlan/18_SATELLITE_OVERLAY_IMPLEMENTATION_PLAN.md`

Explicitly out of this slice:
- runtime apply/persist/restore behavior for map-style satellite toggling.

- User-visible impact:
  - New small 4-tab control at the bottom of map screen.
  - Tapping tabs opens a bottom sheet and selects tab content.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Bottom-sheet selected tab | New UI state holder in map UI layer (Compose state, single owner) | `selectedTab` | Duplicate selected tab in multiple composables |
| Bottom-sheet visibility/expansion | Same UI state holder (single owner) | `isSheetVisible` and sheet state object | Parallel booleans (`showXSheet`, `showYSheet`) for same sheet |
| Closed launcher tabs + open-sheet footer tabs | Same bottom-tabs host state holder | `selectedTab` projected to both surfaces | Independent tab states per surface (`launcherSelectedTab`, `footerSelectedTab`) |
| Tab host state persistence | Same bottom-tabs host state holder | `rememberSaveable`-backed selected tab (and policy-driven visibility restore) | Non-saveable ephemeral state that resets tab context unexpectedly on config change |
| Bottom-sheet/modal arbitration policy | New UI helper in map UI layer (single owner) | `openTab(tab)`, `dismissSheet()` with conflict handling | Implicit ad-hoc checks spread across callsites |
| SkySight option selection (SkySight tab) | Existing forecast preferences/repository owners | Existing forecast settings flows/use-cases | New local mutable mirrors that diverge from forecast SSOT |
| SkySight primary enabled state | Existing forecast preferences/repository owners | Existing `enabled` flow/use-case | Local UI-only enabled mirror that diverges from forecast state |
| Satellite View option state | Deferred SkySight-tab UI state only (`3B`) | Disabled/coming-soon control semantics | Hidden map-style side effects in MVP |
| Previous non-satellite map style restore token | Not used in this slice (`3B` deferred satellite) | Reserved for future strategy-1 implementation only | Premature restore-state logic in MVP |
| Weather tab settings (enable/opacity/animation/frame/render options) | Existing weather preferences owner (`WeatherOverlayPreferencesRepository`) | Shared weather settings contract + `1B` map-tab facade | Local weather setting mirrors that diverge from shared preferences |
| Weather runtime metadata/status (frame, freshness, staleness) | Existing weather runtime owner (`ObserveWeatherOverlayStateUseCase`/`WeatherOverlayViewModel`) | Existing `overlayState` flow | Parallel runtime models computed only in tab UI |
| Weather attribution action | Shared weather attribution helper (weather package) | Explicit link handler contract aligned with existing Weather settings | Hardcoded duplicate attribution links/handlers with drift |
| Weather advanced-route helper copy | Weather tab UI spec (single owner in tab host) | Static helper text + semantics tag | Hidden/implicit route assumptions that rely on user memory |
| Drawer command intent (hamburger toggle vs weather advanced explicit open) | Map UI event/interaction boundary (single owner contract) | Distinct command paths with shared block predicate | Reusing hamburger toggle callback for weather advanced action |
| Navdrawer `Settings` section expansion state | Existing navdrawer state owner (`settingsExpanded`) | Existing navdrawer config state + deterministic open-flow update | Local duplicated expansion flags detached from drawer owner |
| Discoverability-driven `Settings` expansion override | Map UI temporary interaction state | Transient one-shot override semantics | Persisted preference mutation caused by forced expansion |
| Task-panel visibility predicate for modal arbitration | Existing task panel owner (`MapTaskScreenManager.taskPanelState`) | Non-hidden predicate (collapsed+expanded) projected once into arbitration helper | Expanded-only checks that diverge from back-handler behavior |
| Weather advanced sequencing barrier | Tab host interaction coordinator | Sheet-dismiss completion token/callback | Fire-and-forget open drawer before sheet dismissal completion |
| Traffic detail selection state | Existing `MapScreenTrafficCoordinator`/`MapScreenViewModel` owners | Existing selected-target flows | New local mirrors of selected detail IDs in Compose |
| Tab-specific future settings | Existing feature ViewModels/repos (per feature) | Existing flows/use-cases | New hidden global mutable state |

### 2.2 Dependency Direction

Flow remains:

`UI -> domain -> data`

Plan impact:
- Feature is UI composition/state only.
- No new UI->data shortcuts.
- No new domain/timebase logic.
- Preserve existing ViewModel boundaries by handling sheet-open UI state locally in map UI layer.

Likely touched files:
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentOverlays.kt`
- New files under `feature/map/src/main/java/com/trust3/xcpro/map/ui/` for bottom tabs + sheet host state/arbiter.
- New shared weather-settings contract files under `feature/map/src/main/java/com/trust3/xcpro/weather/rain/` (to avoid map->navdrawer coupling).
- `feature/map/src/main/java/com/trust3/xcpro/navdrawer/DrawerMenuSections.kt` (if discoverability expansion behavior needs section-state wiring)
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenContract.kt` (if explicit drawer-open event is added)
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUiEventHandler.kt` (if explicit drawer-open event is handled)
- Optional migration files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/components/MapActionButtons.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayViewModel.kt` (only if new intents are needed for these options)
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/WeatherSettingsViewModel.kt` (only for delegation wiring to shared contract)

### 2.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Bottom-sheet open/select state | Scattered local booleans | Single bottom-tabs host state helper | Prevent duplicate UI state and simplify behavior tests | Host state tests + compose tests |
| Modal coexistence behavior | Implicit composition order | Explicit UI arbitration helper | Prevent dual-sheet conflicts and undefined behavior | compose tests for conflict matrix |
| SkySight option label->parameter mapping | Implicit human-label assumptions | Explicit ID mapping table | Avoid provider label drift and bad selections | unit test for ID mapping |
| SkySight enabled gate behavior | Implicit existing forecast-sheet switch behavior | Explicit tab policy (`Enable Toggle` or `Auto Enable`) | Prevent dead/unresponsive options in SkySight tab | state + compose tests |
| Satellite view deferred behavior (`3B`) | Unclear map-style coupling in tab | Explicit disabled/coming-soon behavior with no style mutation | Avoid hidden map-style side effects and coupling in MVP | compose tests + regression assertion |
| Modal host style for tabbed control | Option ambiguity (`ModalBottomSheet` vs scaffold peek) | Locked modal host policy (`1.0F`) | Prevent refactor churn and behavior drift in V1 | review + compose tests |
| Weather settings mutation path (Weather tab) | Navdrawer weather settings only | Shared mutation path reused by tab + navdrawer via `1B` map-tab facade | Avoid dual-owner drift and inconsistent weather behavior | unit tests + cross-entry compose checks |
| Weather settings contract placement | Navdrawer package owns weather mutation contract | Shared weather settings contract moved/exposed under weather package | Prevent map UI from depending on navdrawer package internals | compile-time deps + tests |
| Weather attribution affordance | Navdrawer weather screen only | Weather tab contract with explicit attribution link action | Preserve provider attribution access after tab rollout | compose + manual link validation |
| `More Weather Settings` drawer action | Toggle-based drawer path | Deterministic drawer-open contract (`1.0G`) | Avoid accidental drawer close or inconsistent behavior | state + compose tests |
| Weather advanced-route discoverability | Implicit user memory of drawer hierarchy and labels | Explicit helper-copy + settings-section expansion contract (`1.0I`) | Prevent Weather-vs-RainViewer confusion and dead-end perception | compose + manual discoverability checks |
| Drawer command separation | Shared hamburger toggle callback reused for multiple intents | Dedicated weather advanced explicit-open path + preserved hamburger toggle path (`1.0J`) | Prevent intent conflation and command regressions | state + compose tests |
| Drawer open intent path from map UI | `ToggleDrawer` emits effects; `SetDrawerOpen(true)` updates state but does not open drawer UI | Add explicit open command path (`OpenDrawer` event/effect or direct open callback) | Avoid "explicit open" silently becoming no-op | event-handler tests + compose flow checks |
| Drawer block enforcement | Runtime effect closes drawer on mode change but does not gate later programmatic `OpenDrawer` calls | Gate drawer-open commands with block predicate (`taskType` + `isAATEditMode`) | Ensure blocked mode cannot be bypassed by explicit open calls | state + compose tests |
| Discoverability expansion persistence | Forced section expand could become persisted preference accidentally | Transient one-shot expansion override by default (`1.0J`) | Preserve user expansion preference unless explicitly changed | state + manual tests |
| Task-panel arbitration predicate | Mixed expanded-only vs non-hidden checks across paths | Normalize to non-hidden predicate (`1.0K`) | Align modal arbitration with back-handler semantics | state + compose tests |
| Weather advanced dismiss/open sequencing | "Dismiss then open" phrasing without completion barrier | Completion-gated open command (`1.0K`) | Prevent transient overlap/race between sheet and drawer | compose/manual sequencing tests |
| Tab switching while modal is open | External launcher can be obscured by modal/scrim | In-sheet footer tabs bound to same selected-tab owner (`1.0H`) | Preserve discrete tab switching UX while sheet is visible | compose tests |
| Tab host state continuity on config change | Implicit non-saveable local state | Saveable selected-tab policy in host state helper | Prevent avoidable tab-context resets | state tests |

### 2.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Forecast FAB direct modal open | Direct `showForecastSheet = true` toggle | Bottom-tab selection + shared sheet host open | Phase 3 (migration) |
| Tab open while detail modal active | Implicit overlap behavior | Explicit dismiss-details-then-open-tab flow | Phase 2 |
| Forecast FAB tap while detail modal active | Independent state paths allow overlap | Dismiss detail selection first, then open forecast modal | Phase 2 |
| Detail selection while forecast modal active | Independent state paths allow overlap | Dismiss forecast modal first, then show detail modal | Phase 2 |
| Forecast FAB tap while task panel is visible | Forecast modal can open while task panel remains visible; back precedence becomes inconsistent | Block forecast-open when task panel is visible (or collapse panel before open) with deterministic policy | Phase 2 |
| Programmatic drawer open while block-mode active | AAT block logic closes drawer only when mode state changes | Guard explicit open commands with block predicate and blocked-state UX | Phase 2 |
| Parallel bottom controls without inset contract | Fixed dp paddings only | Navigation-bar aware bottom padding contract | Phase 2 |
| SkySight option by display name | Label-driven lookups | ID-driven parameter selection (`dwcrit`, `wblmaxmin`) | Phase 1 |
| Satellite style mutation from SkySight tab | Potential direct style mutation attempt | Deferred-mode control (`3B`) with no map-style mutation callbacks | Phase 1 |
| Weather tab local draft settings (potential) | Local mutable toggles/sliders detached from preferences flows | Flow-backed Weather tab controls routed through shared weather settings owner | Phase 1/2 |
| Weather tab direct dependency on navdrawer weather use-case | Map UI importing navdrawer package contracts | Shared weather settings contract under weather package used by both entrypoints | Phase 1 |
| `More Weather Settings` uses drawer toggle semantics | `ToggleDrawer` path may close instead of open under state drift | Explicit drawer-open path (or proven deterministic-open guard + tests) | Phase 1/2 |
| Weather advanced route relies on implicit user memory | Drawer-first route requires extra taps and label translation (`Weather` vs `RainViewer`) | Explicit helper-copy contract + settings-section expansion flow | Phase 1/2 |
| Weather advanced action reuses shared hamburger callback | Hamburger toggle semantics conflated with explicit advanced-open intent | Dedicated weather-open command path while preserving hamburger toggle behavior | Phase 1/2 |
| Forced `Settings` section expansion persists unexpectedly | Discoverability expansion can overwrite user collapse preference via later config saves | Transient expansion override policy with explicit non-persistence checks | Phase 1/2 |
| Task-panel conflict checks rely on expanded-only state | `COLLAPSED` panel can still affect back precedence but may be ignored by modal arbitration | Use non-hidden panel predicate for arbitration and tests | Phase 1/2 |
| Drawer-open command dispatched before sheet dismiss completion | Fire-and-forget sequencing can overlap surfaces during animations | Completion-gated command barrier for weather advanced flow | Phase 1/2 |
| Non-saveable tab context | Local `remember` state resets on config recreation | Saveable tab-state helper policy (`rememberSaveable`) | Phase 1 |

### 2.2C Modal and Panel Precedence Contract

Required behavior:

1. If airspace modal overlay is visible, bottom-tab interactions are disabled.
2. If task panel is non-hidden (`COLLAPSED` or expanded), bottom-tab strip is hidden (or disabled per final UX choice).
3. If traffic details sheet is visible and user taps a tab, details are dismissed first, then tab sheet opens.
4. When bottom-tab sheet is visible, detail-selection callbacks close tab sheet before selecting a detail target.
5. Back press with bottom-tab sheet visible dismisses that sheet before task panel/back-stack handling.
6. If traffic details sheet is visible and user opens forecast modal, details are dismissed first.
7. If forecast modal is visible and user opens a traffic detail, forecast modal is dismissed first.
8. Legacy forecast FAB open path must not open forecast modal while task panel is visible.
9. If drawer-open is blocked by task-edit mode, `More Weather Settings` action must be disabled and explicit about why.
10. Drawer-first weather path must remain discoverable in-place via helper copy (`Settings -> General -> RainViewer`).
11. If navdrawer `Settings` section is collapsed, weather advanced action must expand it before/while opening drawer.
12. While drawer-block predicate is active, hamburger toggle requests must not reopen drawer from closed state.
13. Weather advanced action must dispatch drawer-open only after tab-sheet dismissal completion barrier is satisfied.

### 2.2D Layering and Input Routing Contract

Required draw/input policy:

1. Bottom tab strip must render above map gesture overlays.
2. Bottom tab strip must consume its own taps; map tap/long-press handlers must not fire from tab interactions.
3. Sheet scrim taps dismiss the sheet only; no map gesture callbacks should fire on the same gesture.
4. When sheet is visible, tab strip remains interactive only if explicitly desired; otherwise disable to prevent rapid state thrash.
5. Maintain existing high-priority overlays:
   - task top panel (`zIndex` ~70) remains above base map content;
   - airspace modal (`zIndex` ~80) blocks tab interactions.
6. Host insertion order in `MapScreenContent` root `Box` must be explicit so tabs render above map/action layers but below detail modal sheets.
7. While sheet is visible, tab-to-tab switching is provided by in-sheet footer tabs; external launcher tabs may be hidden/disabled.

### 2.2E Bottom Lane Reservation Contract

Required layout policy:

1. Keep tab strip compact and centered so bottom-end action lane (AAT/replay/demo FABs) remains usable.
2. Apply navigation-bar insets to tab strip and sheet anchor point.
3. In DEBUG builds, bottom-start debug panels must not fully hide tabs; adjust spacing or offset as needed.
4. If screen width is constrained, tabs remain tappable and do not overlap system gesture/nav areas.
5. Keep compact visual tabs while preserving reliable touch targets (target 48dp interaction area even if visual chrome is smaller).

### 2.2F Nested Sheet Prohibition

Required:

- Do not compose `ModalBottomSheet` inside another `ModalBottomSheet`.
- Forecast migration must extract forecast content from its current wrapper before hosting it in the tabbed sheet.

### 2.2G Parameter Availability and Alias Contract

Required:

1. `Thermal Tops` must resolve to `ForecastParameterId("dwcrit")`.
2. `Convergence` must resolve to `ForecastParameterId("wblmaxmin")`.
3. Mapping must not depend on provider display text (`Thermal Height` may change).
4. If either parameter is absent from current catalog, show option disabled and keep current selection stable.
5. `Satellite View` follows locked `3B` deferred strategy (Section 1.2) and cannot be silently bound to a nonexistent forecast parameter.
6. SkySight UI controls must reflect forecast selection semantics (ID-based chip/selector behavior).

### 2.2H Forecast Capability Parity Contract

Required for rollout safety:

1. Do not remove access to existing forecast controls (wind/time/opacity/legend) in this initial slice.
2. Forecast FAB removal is only allowed after explicit parity sign-off.
3. If parity is not implemented, keep dual-entry path temporarily (existing forecast sheet + new tab host).
4. While dual-entry remains, legacy forecast FAB open path must honor task-panel gating and single-modal arbitration.

### 2.2I Weather Ownership, Scope, and Attribution Contract

Required:

1. Weather tab uses `1B` map-scoped facade/ViewModel that delegates to shared weather settings contracts.
2. Shared weather settings contracts must live under weather package ownership (not navdrawer package ownership).
3. Weather-tab runtime status reads must use existing weather runtime state owner (`overlayState` flow path).
4. Weather tab uses `2A` MVP controls only:
   - enable switch,
   - opacity slider,
   - attribution link action,
   - `More Weather Settings` drawer action.
5. Existing navdrawer weather settings entrypoint remains available until weather parity sign-off.
6. Weather attribution link/action must remain visible and reachable in Weather-tab UX.
7. Tab and navdrawer weather entrypoints must remain state-consistent via shared flows with no stale local caches.
8. `More Weather Settings` action must dismiss tab sheet before opening drawer.
9. If drawer-open is blocked by task-edit mode, `More Weather Settings` action must be disabled with explicit reason text.
10. Weather tab must provide helper copy for V1 drawer-first route (`Settings -> General -> RainViewer`).
11. If navdrawer `Settings` is collapsed, weather advanced action must expand it before/while opening drawer.
12. Weather advanced action must await sheet-dismiss completion barrier before drawer-open dispatch.

### 2.2J Single-Modal Surface Contract (Modal Strategy)

Required:

1. At most one `ModalBottomSheet` is visible on map surface at a time.
2. Opening tabbed modal must dismiss active details/forecast modal sheets first.
3. Opening detail/forecast modal while tabbed modal is visible must dismiss tabbed modal first.
4. Modal precedence and dismissal ordering must be deterministic and test-covered.
5. Modal host for tabbed control follows locked policy from Section 1.0F.
6. Arbitration logic must not rely solely on `MapModalManager.isAnyModalOpen()` because it does not track modal bottom-sheet state.
7. Existing forecast FAB path must also comply with single-modal arbitration while dual-entry paths remain.

### 2.2K Modal Expansion Policy Contract

Required:

1. Tabbed modal host opens full-only in V1 (`skipPartiallyExpanded = true`).
2. Existing detail-sheet partial/full behavior can remain as-is in V1 unless explicitly normalized.
3. Mixed expansion policies must not break single-modal arbitration or dismissal ordering.
4. If normalization is later required, treat it as a separate scoped change with dedicated regression tests.

### 2.2L Dual Tab Surface Synchronization Contract

Required:

1. Closed-state launcher tabs and open-state in-sheet footer tabs must read/write the same selected-tab owner.
2. A tab switch from either surface must update visible content identically.
3. No independent tab selection state is allowed per surface.
4. In-sheet footer tabs remain visually compact while meeting touch-target policy from Section 2.2E.

### 2.2M Drawer-Block Enforcement Contract

Required:

1. Drawer-block predicate for map route must be explicit (`taskType == AAT && isAATEditMode` under current rules).
2. Blocked state must gate explicit/programmatic drawer-open commands (not gesture path only).
3. `More Weather Settings` action must honor the same predicate used by drawer-open guard.
4. Existing hamburger toggle open path must also honor the same predicate when drawer is currently closed.
5. When blocked, UI must present deterministic behavior (disabled action + reason text), never silent no-op.

### 2.2N Tab Host State Persistence Contract

Required:

1. Selected-tab state should be saveable across configuration recreation (`rememberSaveable` in UI host state helper).
2. Visibility restore behavior is policy-driven and test-covered (either restore or explicit-reset contract).
3. State restoration must not violate single-modal arbitration or precedence rules.

### 2.2O Weather Advanced-Route Discoverability Contract

Required:

1. V1 route remains drawer-first:
   - drawer open -> `Settings` -> `General` -> `RainViewer` -> weather settings screen.
2. Weather tab must present helper copy that references the exact current route labels.
3. `More Weather Settings` flow should expand navdrawer `Settings` section before/while opening drawer.
4. Discoverability must be deterministic even when `settingsExpanded` was previously persisted as collapsed.
5. Direct deep-link callback plumbing is not required in this slice as long as contract items above are met.

### 2.2P Discoverability Expansion Persistence Contract

Required:

1. Any forced `Settings` section expansion used for discoverability is transient by default.
2. Forced expansion must not be treated as a user preference mutation unless user explicitly toggles section state.
3. Subsequent unrelated drawer-config saves must not accidentally persist forced expansion state as user intent.
4. If persistence behavior differs from this default, document it explicitly and test expected UX.

### 2.2Q Task-Panel Predicate and Drawer Sequencing Barrier Contract

Required:

1. Tabbed-sheet arbitration must use task-panel non-hidden predicate to align with map back-handler behavior.
2. `COLLAPSED` task-panel state is treated as visible for conflict policy unless explicitly overridden in approved UX contract.
3. Weather advanced flow must await sheet-dismiss completion (or equivalent deterministic barrier) before dispatching drawer-open command.
4. Sequencing barrier implementation must be test-covered against fast repeated taps and animation-in-progress cases.

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Bottom-sheet animation timing | Compose frame clock (UI visual only) | No domain semantics |

Explicitly forbidden:
- Monotonic vs wall comparisons.
- Replay vs wall comparisons.
- Any new direct clock calls in domain/fusion.

### 2.4 Threading and Cadence

- Dispatcher ownership: Main only (UI rendering/interaction).
- Primary cadence source: user gestures only.
- Hot-path budget: first visible response to tab tap should feel immediate (< 100 ms target on typical devices).

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence: none introduced; UI-only control surface.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Duplicate sheet state owners | SSOT rules | review + unit/compose test | new bottom-sheet host tests |
| Multiple modal sheets visible concurrently | interaction determinism | compose tests + review | single-modal arbitration tests |
| Forecast/detail modal overlap on legacy paths | interaction determinism | explicit arbitration on forecast/detail open paths + tests | legacy-path arbitration tests |
| Modal arbitration keyed to wrong owner (`MapModalManager` only) | correctness | track modal-sheet visibility in tab/forecast/detail host state | modal-owner boundary tests |
| Business logic in UI | UI rules | review + enforceRules | affected map UI files |
| ViewModel bloat/line budgets | maintainability gates | enforceRules line budget | avoid adding to capped files |
| Back navigation regressions | UDF/interaction stability | compose/manual test | new back behavior tests |
| Modal coexistence regressions | deterministic interaction behavior | compose test | new conflict-matrix tests |
| Navigation-bar overlap | UI usability | compose/manual test | inset + bottom layout tests |
| Tap click-through into map gestures | interaction correctness | compose/manual test | tab/scrim interaction tests |
| Bottom lane overlap with action controls | UI usability | compose/manual test | compact-width + lane tests |
| Tab state drift between launcher tabs and in-sheet footer tabs | SSOT/UX consistency | single-owner state contract + compose tests | dual-surface tab-state tests |
| Bad option mapping from label drift | correctness | unit test | overlays mapping test |
| Satellite option mistakenly treated as active overlay | correctness | review + unit test | deferred strategy tests |
| Incorrect toggle semantics for forecast primary selection | behavior regression | unit + compose test | overlays selection behavior tests |
| SkySight options appear disabled/dead when forecast primary overlays are off | UX/interaction regression | state + compose tests | skysight activation policy tests |
| Satellite control causes map-style side effect in deferred mode | correctness/UX regression | unit + compose tests | deferred strategy no-style tests |
| Forecast feature loss from early FAB removal | functional regression | staged rollout gate | parity checklist + manual validation |
| Weather tab writes bypass shared preferences owner | SSOT rules | unit + compose tests | weather-tab mutation routing tests |
| Weather tab depends on navdrawer-only settings contracts | layering/maintainability | review + unit tests | shared weather-contract extraction tests |
| Weather tab and navdrawer weather settings diverge | UX/state consistency | cross-entry manual + compose checks | dual-entry consistency tests |
| Weather attribution affordance lost in tab migration | compliance/UX | compose + manual validation | attribution-link presence/action tests |
| More Weather Settings action opens drawer before sheet dismissal | modal sequencing | compose/manual test | drawer-action sequencing tests |
| More Weather Settings drawer-open misfires due toggle semantics | interaction determinism | explicit-open contract + state tests | drawer-open determinism tests |
| More Weather Settings appears broken when drawer is blocked by task-edit mode | UX/interaction determinism | disabled-state policy + task-mode tests | drawer-blocked behavior tests |
| Programmatic drawer-open bypasses AAT block policy | interaction determinism | enforce Section 2.2M guard on explicit open paths | drawer-block enforcement tests |
| Forecast modal opens while task panel is visible (legacy FAB path) causing back-order conflicts | interaction determinism | enforce forecast/task-panel gating + back tests | legacy forecast task-panel gating tests |
| Selected tab resets unexpectedly on config recreation | UX continuity | enforce Section 2.2N saveable state policy + tests | tab-state persistence tests |
| Drawer-first Weather advanced path is perceived as broken due no deep-link | UX expectation | explicit V1 contract + manual UX checks | drawer-first expectation checks |
| Weather advanced path discoverability degrades due `Weather` vs `RainViewer` naming and collapsed `Settings` section | UX expectation | enforce Section 2.2O helper-copy + section-expansion contract | weather advanced-route discoverability tests |
| Hamburger toggle reopens drawer while blocked mode is active | interaction determinism | enforce Section 2.2M guard on toggle-open branch | drawer-block toggle-path tests |
| Forced discoverability expansion unintentionally persists as drawer preference | UX consistency | enforce Section 2.2P transient expansion policy + tests | settings-expansion persistence tests |
| Task-panel arbitration ignores collapsed state while back handler treats it as visible | interaction determinism | enforce Section 2.2Q non-hidden predicate policy | task-panel predicate alignment tests |
| Drawer opens before sheet-dismiss completion causing transient overlap | modal sequencing | enforce Section 2.2Q sequencing barrier | dismiss-open sequencing barrier tests |

## 3) Data Flow (Before -> After)

Before:

```
Forecast FAB tap -> local showForecastSheet -> ForecastOverlayBottomSheet
```

After (target):

```
Bottom Tab Tap -> BottomSheetTabsHost state helper
-> arbitration (dismiss detail modal if needed, enforce panel/modal rules)
-> ModalBottomSheet host state (selected tab + visible)
-> Selected tab content (placeholder now, feature controls later)
```

Dual tab-surface sync flow (target):

```
Closed launcher tab tap -> selectedTab (single owner)
Open-sheet footer tab tap -> selectedTab (same owner)
selectedTab -> tab content in shared modal host
```

SkySight option intent flow (target):

```
SkySight option tap
-> resolve option ID mapping table
-> enforce overlays activation policy (Section 1.1A)
-> if available:
     forecast preferences/use-case update (dwcrit/wblmaxmin)
   else:
      disabled-state UX for `Satellite View` (`3B` deferred behavior)
```

Weather option intent flow (target):

```
Weather tab control change
-> MapWeatherTab facade/ViewModel (`1B`)
-> shared weather settings contract (weather package)
-> WeatherOverlayPreferencesRepository (SSOT)
-> ObserveWeatherOverlayStateUseCase
-> WeatherOverlayViewModel.overlayState
-> MapWeatherOverlayEffects -> MapOverlayManager.setWeatherRainOverlay(...)
```

Weather attribution flow (target):

```
Weather tab attribution tap -> shared weather attribution link handler -> external browser
```

Weather advanced-settings flow (MVP):

```
Weather tab "More Weather Settings" tap
-> dismiss tab sheet
-> await/confirm sheet dismissal completion barrier
-> apply transient `Settings`-section discoverability expansion (no preference persist-by-default)
-> trigger dedicated weather-advanced explicit drawer-open action (non-toggle semantics)
-> user taps `General`
-> user taps `RainViewer` (current weather settings card label)
```

Weather advanced-settings action when drawer is blocked (target):

```
Task-edit mode blocks drawer
-> Weather tab shows disabled "More Weather Settings" action with reason text
-> no hidden open-drawer attempt
```

Drawer-open guard flow (target):

```
Explicit open drawer request
-> evaluate drawer-block predicate (`taskType`, `isAATEditMode`)
-> if blocked: reject open + keep explanatory UI state
-> if allowed: emit/open drawer command
```

Hamburger toggle flow with block guard (target):

```
Hamburger tap
-> if drawer currently open: allow close path
-> if drawer currently closed: evaluate drawer-block predicate
   -> blocked: reject open
   -> allowed: execute toggle-open path
```

Legacy forecast flow while dual-entry remains (required arbitration):

```
Forecast FAB tap
-> if task panel visible: block action (or collapse panel first per policy)
-> else continue
-> dismiss active detail modal selection (if any)
-> open forecast modal

Detail selection tap while forecast modal open
-> dismiss forecast modal
-> show selected detail modal
```

Detail tap flow (target):

```
Map detail tap -> close tabbed sheet if open -> select target via existing callbacks
-> existing OGN/thermal/ADS-B detail sheets
```

Core pipeline remains unchanged:

```
Sensors -> Repository (SSOT) -> UseCases -> ViewModel -> UI
```

## 4) Implementation Phases

### Phase 0 - Baseline and Contracts

- Goal:
  Lock UI contract, conflict matrix, and line-budget-safe integration boundaries.

- Deliverables:
  - `MapBottomSheetTab` enum/spec.
  - Fixed interaction contract:
    - tap same tab behavior,
    - task-panel coexistence,
    - detail-modal coexistence,
    - airspace-modal coexistence.
  - Fixed layering contract:
    - tab z-order,
    - click-through behavior,
    - bottom lane reservation envelope.
  - Fixed SkySight mapping contract:
    - `Thermal Tops` -> `dwcrit`
    - `Convergence` -> `wblmaxmin`
    - `Satellite View` strategy from Section 1.2.
  - Locked defaults from Section 1.0E (`1B`, `2A`, `3B`, `4A`).
  - Fixed SkySight activation contract from Section 1.1A.
  - Fixed Weather ownership contract from Section 1.0B.
  - Fixed Weather MVP scope/parity gate from Section 1.0C.
  - Fixed Weather attribution + dual-entry contract from Section 1.0D.
  - Fixed modal host policy from Section 1.0F.
  - Fixed drawer-open determinism contract from Section 1.0G.
  - Fixed dual-tab-surface and drawer-blocked policy from Section 1.0H.
  - Fixed drawer-first discoverability contract from Section 1.0I.
  - Fixed drawer command separation + expansion persistence contract from Section 1.0J.
  - Fixed task-panel visibility + sequencing contract from Section 1.0K.
  - Fixed drawer-block enforcement contract from Section 2.2M.
  - Fixed tab-state persistence contract from Section 2.2N.
  - Fixed weather advanced-route discoverability contract from Section 2.2O.
  - Fixed discoverability expansion persistence contract from Section 2.2P.
  - Fixed task-panel predicate + sequencing barrier contract from Section 2.2Q.
  - Fixed forecast parity gate from Section 2.2H.

- Exit criteria:
  - Design contract approved.
  - No architecture deviations required.

### Phase 1 - New UI Components (Isolated)

- Goal:
  Add new focused composables without changing existing map behavior yet.

- Planned files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabsModels.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabsState.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabsLayoutPolicy.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabsBar.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabsHost.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabsFooter.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabsContent.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapWeatherTabViewModel.kt` (facade for `1B`)
  - Shared weather settings contract files under `feature/map/src/main/java/com/trust3/xcpro/weather/rain/`
  - Optional drawer-event hardening files:
    - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenContract.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUiEventHandler.kt`

- Behavior:
  - 4 discrete compact tabs.
  - Confirmed initial tab order:
    - Tab 1: `Weather`
    - Tab 2: `SkySight`
  - `SkySight` tab has concrete content:
    - `Thermal Tops`
    - `Convergence`
    - `Satellite View`
  - `Satellite View` is rendered disabled/coming-soon (`3B`) with no map-style mutation in this slice.
  - `Weather` tab has concrete MVP content (`2A`), routed through `1B` shared-owner facade:
    - enable switch,
    - opacity slider,
    - attribution link action,
    - `More Weather Settings` drawer action.
  - Other tabs may remain placeholder content panes.
  - Single local owner for selected tab + visibility.
  - Tab host selected-tab state follows saveable policy from Section 2.2N.
  - One shared modal host only for tabbed flow.
  - Open modal includes compact in-sheet footer tabs for switching.
  - Centered compact-width bar policy to preserve bottom-end lane.
  - Insets-aware padding via navigation bar insets.
  - Test tags for stable compose tests.
  - ID-based mapping helper for SkySight options (with alias labels and availability checks).
  - SkySight control model matches forecast primary-selection semantics (chip/selector, not independent switches).
  - SkySight activation behavior is explicit (`Enable Toggle` or `Auto Enable`).
  - Compact visuals maintain reliable tap targets.
  - Drawer-blocked state is surfaced in Weather tab (`More Weather Settings` disabled with reason).
  - Weather tab includes explicit drawer-first helper copy (`Settings -> General -> RainViewer`).
  - Weather advanced action uses dedicated explicit drawer-open request path (no shared hamburger callback reuse).
  - Discoverability-driven `Settings` section expansion follows transient (non-persist-by-default) policy.
  - Weather advanced action enforces dismissal-completion barrier before drawer-open dispatch.

- Exit criteria:
  - Host composables compile.
  - No regressions in existing map UI features.

### Phase 2 - MapScreen Integration

- Goal:
  Place bottom tab strip and sheet host into map UI layering with explicit coexistence rules.

- Planned files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentOverlays.kt`

- Integration rules:
  - Keep deltas small in high-risk files; extract logic into new files.
  - Ensure tab strip stays compact and visually discrete.
  - Apply navigation-bar-safe bottom padding.
  - Ensure tab strip is composed above map gesture layer (no click-through).
  - Hide/disable external launcher tabs when sheet is open and use in-sheet footer tabs for tab switching.
  - Hide/disable tabs when task panel is non-hidden (collapsed or expanded; per approved contract).
  - Resolve tab-sheet/detail-sheet coexistence via explicit wrappers.
  - Reserve bottom-end lane for existing AAT/replay/demo controls.
  - Handle DEBUG bottom-start debug panel coexistence.
  - Keep `MapScreenViewModel` and `MapScreenScaffoldInputs` untouched unless strictly required.
  - Do not hardcode provider names for parameter lookup; use IDs only.
  - Specify host insertion order in `MapScreenContent` so layering works across composition subtrees.
  - Do not remove existing forecast control entrypoint in this phase.
  - Do not remove existing navdrawer weather settings entrypoint in this phase.
  - For `2A`, do not add direct weather-route navigation callback plumbing in this slice; use existing drawer-open action for `More Weather Settings`.
  - `More Weather Settings` action sequence must be deterministic: dismiss sheet first, then open drawer.
  - `More Weather Settings` action must await dismissal-completion barrier before drawer-open command dispatch.
  - `More Weather Settings` action should set/ensure navdrawer `Settings` section expanded before/while opening drawer.
  - Forced `Settings` section expansion for discoverability must be transient by default (no preference persistence unless explicit user action).
  - Preserve hamburger widget toggle semantics; do not repurpose hamburger callback as weather advanced explicit-open action.
  - Ensure single-modal arbitration across tabbed modal and existing detail/forecast modals.
  - Ensure legacy forecast FAB/detail modal paths also follow single-modal arbitration while dual-entry remains.
  - Ensure legacy forecast FAB open path is gated when task panel is visible.
  - Do not treat `MapModalManager.isAnyModalOpen()` as authoritative for modal-sheet visibility.
  - Do not rely on `SetDrawerOpen(true)` for explicit drawer open without effect emission; use an explicit open command path.
  - Do not rely on drawer-gesture blocking for safety; `NavigationDrawer` gestures are disabled, so explicit open commands must be block-guarded.
  - Ensure drawer block guard is applied on hamburger toggle-open branch as well (not just dedicated explicit-open path).

- Exit criteria:
  - Tapping each tab opens sheet and switches content.
  - While sheet is open, in-sheet footer tabs switch content using the same selected-tab owner.
  - Tab labels/order match contract (`Weather`, `SkySight`, ...).
  - Back dismisses sheet reliably.
  - No blocked map gestures outside sheet area.
  - No bottom overlap with system nav bar.
  - No overlap with bottom-end action lane in normal + replay/debug states.

### Phase 3 - Optional Forecast Path Consolidation (Post-Parity)

- Goal:
  Unify forecast entry paths only after parity with existing forecast controls is implemented and verified.

- Planned files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/components/MapActionButtons.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentOverlays.kt`

- Migration approach:
  - Extract reusable forecast sheet content from modal wrapper.
  - In `SkySight` tab:
    - wire `Thermal Tops` and `Convergence` via forecast settings SSOT using IDs.
    - keep `Satellite View` deferred/disabled per locked `3B` strategy (Section 1.2), unless scope is explicitly expanded.
    - keep/implement explicit overlays activation control behavior from Section 1.1A.
  - Host that content inside the new tabbed sheet.
  - Remove old direct forecast FAB trigger path only after parity checklist passes.
  - Keep navdrawer weather settings route until Weather parity checklist passes.
  - Ensure no nested modal-sheet composition remains.

- Exit criteria:
  - Single authoritative forecast entry path remains.
  - No nested modal-sheet usage.

### Phase 4 - Hardening and Tests

- Goal:
  Add regression coverage and verify architecture guards.

- Planned tests:
  - New state-helper unit tests for selected-tab/open-dismiss conflict behavior.
  - New Robolectric compose tests for tab strip visibility and tab tap behavior.
  - Dual-surface tab sync tests (launcher tabs + in-sheet footer tabs share one selected-tab state).
  - Back behavior tests for sheet open/close with task panel visible.
  - Coexistence tests (detail modal vs tab sheet).
  - Single-modal arbitration tests (never render two modal sheets at once).
  - Legacy-path arbitration tests (forecast FAB + detail selection sequencing).
  - Navigation-bar inset layout smoke checks.
  - Click-through tests (tab/scrim gestures must not reach map tap callbacks).
  - Bottom-lane overlap checks with replay/demo/AAT controls enabled.
  - Weather ownership tests (Weather-tab mutations route to shared preferences owner).
  - Weather dual-entry consistency tests (tab + navdrawer reflect same state).
  - Weather attribution-link presence/action tests.
  - SkySight option mapping tests (`Thermal Tops`/`Convergence` IDs + deferred satellite behavior).
  - SkySight selection-semantics tests (primary/secondary behavior parity with forecast use-cases).
  - SkySight activation-policy tests (disabled-state avoidance and enable behavior).
  - Deferred-satellite tests: `Satellite View` is disabled and does not trigger map-style changes.
  - `More Weather Settings` tests: drawer action is triggered only after tab sheet dismissal.
  - Drawer-open determinism tests: advanced-settings action cannot close drawer by toggle misfire.
  - Drawer-blocked behavior tests: weather advanced action is disabled with reason while drawer is task-blocked.
  - Drawer-block enforcement tests: explicit/programmatic open requests are rejected while block predicate is true.
  - Drawer-block toggle-path tests: hamburger tap cannot reopen drawer from closed state while block predicate is true.
  - Weather advanced-route discoverability tests: helper copy and collapsed-settings-section path remain understandable and deterministic.
  - Discoverability expansion persistence tests: forced section expansion does not persist as user preference by default.
  - Task-panel predicate alignment tests: `COLLAPSED` state follows same modal arbitration policy as expanded states.
  - Dismiss-open sequencing barrier tests: rapid `More Weather Settings` taps do not open drawer before sheet-dismiss completion.
  - Legacy forecast/task-panel gating tests: forecast modal cannot open while task panel is visible.
  - Tab-state persistence tests: selected tab follows saveable policy across configuration recreation.
  - Drawer-first Weather settings UX checks: behavior is intentional and discoverable in V1.
  - Forecast parity checklist tests before any FAB-removal change.

- Exit criteria:
  - Required checks pass.
  - No line-budget regressions in guarded files.
  - `PIPELINE.md` updated if forecast path wiring changes in Phase 3.

## 5) Test Plan

- Unit/compose tests to add:
  - `feature/map/src/test/java/com/trust3/xcpro/map/ui/MapBottomSheetTabsStateTest.kt`
    - `openTab()` selects and opens.
    - selecting same tab follows approved toggle policy.
    - dismiss clears visibility but preserves selected tab policy.
    - conflict rules (task panel non-hidden/details modal visible) are enforced.
  - `feature/map/src/test/java/com/trust3/xcpro/map/ui/MapBottomSheetTabsHostTest.kt`
    - tab tap opens sheet.
    - selected tab changes visible content.
    - in-sheet footer tab taps update the same selected-tab owner as launcher tabs.
    - first two tabs are labeled and ordered:
      - `Weather`
      - `SkySight`
    - dismiss closes sheet.
    - compact strip stays visible in allowed states.
    - tab taps do not call underlying map tap callbacks.
    - sheet scrim dismiss does not call map tap callbacks.
    - no second `ModalBottomSheet` is shown while tabbed modal is visible.
    - forecast FAB path does not create a second modal sheet when detail modal is active.
    - detail selection while forecast modal is open dismisses forecast first.
    - `Weather` tab renders MVP weather controls selected in Section 1.0C.
    - `Weather` tab control changes dispatch through shared weather settings owner path.
    - `Weather` tab exposes attribution link/action.
    - `SkySight` tab renders options:
      - `Thermal Tops`
      - `Convergence`
      - `Satellite View`
    - `Thermal Tops` dispatches selection for `dwcrit`.
    - `Convergence` dispatches selection for `wblmaxmin`.
    - `Satellite View` is disabled/coming-soon in this slice (`3B`).
    - `Satellite View` does not dispatch any map-style mutation callback in this slice.
    - Thermal Tops/Convergence controls preserve forecast primary-selection semantics.
    - SkySight controls are not dead when forecast primary overlays start disabled.
    - `More Weather Settings` action dismisses tab sheet before triggering drawer-open action.
    - `More Weather Settings` action uses deterministic drawer-open semantics (no toggle-close regression).
    - `More Weather Settings` action is disabled with explicit reason when drawer is blocked by task-edit mode.
    - `Weather` tab shows helper copy for drawer-first route (`Settings -> General -> RainViewer`).
    - `More Weather Settings` action expands navdrawer `Settings` section before/while drawer open.
    - `More Weather Settings` action uses dedicated explicit-open path and does not reuse hamburger toggle callback.
    - Forced discoverability expansion does not persist as user preference by default.
    - Hamburger tap while blocked mode is active cannot reopen drawer from closed state.
    - Task panel `COLLAPSED` state is treated as panel-visible for tabbed-sheet conflict policy.
    - Weather advanced action does not dispatch drawer-open before sheet-dismiss completion barrier.
    - legacy forecast FAB path cannot open forecast modal while task panel is visible.
  - Update/add map UI test for coexistence behavior with existing detail-sheet callbacks.
  - Add map UI test that validates modal arbitration does not depend on `MapModalManager` airspace-only flags.
  - Add event-handler unit test (if explicit drawer-open event path is introduced) to ensure open command is emitted without toggle dependence.
  - If/when Phase 3 is attempted: add parity checklist assertions for wind/time/opacity controls.

- Manual checks:
  - Small-screen portrait and landscape.
  - Gesture-navigation device (bottom nav bar overlap).
  - With task panel visible.
  - With task panel collapsed (minimized bar visible) and with task panel expanded.
  - With OGN/ADS-B details sheets.
  - With airspace modal open.
  - With replay running.
  - With debug panels visible (DEBUG build) and demo/replay bottom-end buttons shown.
  - Back press with task panel non-hidden (collapsed or expanded) + tab sheet open (sheet must dismiss first).
  - While sheet is open, in-sheet footer tabs switch tabs and stay in sync with launcher-tab selection.
  - With detail sheet open, tapping tab dismisses detail sheet before tabbed modal appears.
  - With detail sheet open, forecast FAB tap does not produce overlapping modal sheets.
  - With task panel visible, forecast FAB does not open forecast modal until panel policy is satisfied.
  - With task panel collapsed (not hidden), tabbed-sheet open policy matches configured panel-visible contract.
  - With forecast modal open, selecting OGN/thermal/ADS-B detail does not produce overlapping modal sheets.
  - Existing forecast advanced controls remain reachable in initial slice.
  - Existing navdrawer Weather settings remain reachable in initial slice.
  - Change Weather in tab and verify navdrawer Weather screen reflects same value (and vice versa).
  - Weather attribution link opens successfully from tabbed flow.
  - `More Weather Settings` action closes tab sheet and opens drawer (no overlapping surfaces).
  - Rapid repeated `More Weather Settings` taps still honor dismiss-completion barrier (no drawer-open before sheet dismissed).
  - In AAT edit mode (drawer blocked), `More Weather Settings` is disabled with clear reason text.
  - `More Weather Settings` drawer-first behavior is understandable in V1 (no direct weather deep-link expected).
  - Starting with navdrawer `Settings` collapsed, `More Weather Settings` still exposes a clear path to `General` then `RainViewer`.
  - Weather-tab helper copy explicitly references `RainViewer` naming to avoid ambiguity.
  - In blocked mode with drawer closed, tapping hamburger does not reopen drawer.
  - After discoverability-triggered section expansion, reopening app/route still respects original persisted `settingsExpanded` preference unless user explicitly changed it.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

Note:
- `feature/map` currently has no dedicated `src/androidTest` tree in this repo snapshot.
- Primary automated coverage for this change should be JVM + Robolectric compose tests.

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Overlap with existing bottom controls (debug/demo/action elements) | Medium | Explicit bottom insets + z-index contract + manual small-screen pass | XCPro Team |
| Duplicate sheet states during migration | Medium | Single host state owner; remove parallel toggles in consolidation phase | XCPro Team |
| Regressions from touching large files near line budgets | High | New isolated files; minimal adapter calls in existing files | XCPro Team |
| Back handling conflicts with task/modal flows | Medium | Conflict matrix + explicit tests for back precedence and sheet dismissal | XCPro Team |
| Forecast migration churn (large sheet file) | Medium | Stage migration; extract content component before removing old path | XCPro Team |
| Hidden dependency on old forecast FAB flow | Medium | Keep temporary dual-path behind explicit feature parity check | XCPro Team |
| Two modal sheets visible simultaneously | High | Enforce single-modal arbitration contract + compose tests | XCPro Team |
| Mixed modal expansion policies create inconsistent gestures | Medium | Lock tabbed-modal full-open policy; defer normalization with dedicated scope | XCPro Team |
| Selected tab drifts between closed launcher and open footer surfaces | Medium | Enforce Section 2.2L single owner and dual-surface sync tests | XCPro Team |
| Click-through map interactions from tab/scrim gestures | High | Explicit input-routing tests + layering policy | XCPro Team |
| Nested modal sheet regression | Medium | Prohibit nesting; enforce single sheet host in code review | XCPro Team |
| Thermal Tops label mismatch with provider label | Medium | Alias label to stable ID `dwcrit`; avoid string-name matching | XCPro Team |
| Satellite View expectation mismatch | High | Explicit locked `3B` behavior + acceptance criteria + staged delivery | XCPro Team |
| SkySight controls behave unlike forecast model | Medium | Use ID-driven selector semantics and regression tests | XCPro Team |
| SkySight options unusable while forecast primary overlays disabled | High | Add explicit activation policy and tests (Section 1.1A) | XCPro Team |
| Deferred satellite option accidentally mutates map style | Medium | Enforce `3B` disabled behavior and no-style-mutation tests | XCPro Team |
| Early forecast FAB removal causes feature loss | High | Make Phase 3 optional and parity-gated | XCPro Team |
| Weather tab writes routed through wrong owner path | High | Enforce Section 1.0B owner contract and test mutation routing | XCPro Team |
| Map tab depends on navdrawer weather contracts directly | Medium | Move/expose shared weather settings contract under weather package | XCPro Team |
| Weather tab and navdrawer weather settings diverge | Medium | Keep shared SSOT flow path + dual-entry consistency tests | XCPro Team |
| Weather attribution visibility lost during tab rollout | Medium | Keep explicit attribution-link contract and test/validate action | XCPro Team |
| Drawer open requested while weather tab sheet remains open | Medium | Enforce dismiss-sheet-then-open-drawer ordering with tests | XCPro Team |
| Drawer-open path closes drawer due toggle-state drift | Medium | Prefer explicit open command path + deterministic state tests | XCPro Team |
| Drawer blocked in AAT edit mode makes advanced weather action appear broken | Medium | Disable action with reason while blocked; test blocked-state behavior | XCPro Team |
| Drawer-first weather advanced path is unclear because UI label says `Weather` but settings entry says `RainViewer` | Medium | Add helper-copy contract + collapsed-settings expansion behavior + tests | XCPro Team |
| Hamburger toggle path reopens drawer while blocked mode is active | High | Apply block guard to toggle-open branch and test blocked hamburger behavior | XCPro Team |
| Discoverability-triggered section expansion unintentionally persists as user preference | Medium | Lock transient default policy and add persistence-behavior tests | XCPro Team |
| Task-panel `COLLAPSED` state behaves differently from expanded states and causes back/modal conflicts | Medium | Normalize arbitration to non-hidden predicate and test collapsed-state paths | XCPro Team |
| Drawer opens before sheet-dismiss completion under fast taps | Medium | Add completion-gated sequencing barrier with rapid-tap tests | XCPro Team |
| Forecast FAB opens modal while task panel is visible, causing back-order confusion | Medium | Add deterministic forecast/task-panel gating and regression tests | XCPro Team |

## 7) Acceptance Gates

- 4 discrete compact tabs are visible at bottom on MapScreen.
- Tabbed control uses `ModalBottomSheet` host policy (`1.0F`) in this slice.
- Persistent peek-sheet / drag-up-open behavior is not required in this slice.
- First two tabs are labeled and ordered:
  - `Weather`
  - `SkySight`
- Tab tap opens sheet and selects the tab.
- While sheet is open, compact in-sheet footer tabs remain available for tab switching.
- Launcher tabs and in-sheet footer tabs share one selected-tab state owner.
- Sheet can be dismissed and reopened without stale state.
- `Weather` tab exposes MVP weather controls defined in Section 1.0C.
- `Weather` tab updates are routed through shared weather settings owner contract (Section 1.0B).
- `Weather` tab includes accessible source attribution link/action.
- `Weather` tab `More Weather Settings` action dismisses sheet first, then opens drawer.
- `More Weather Settings` action uses deterministic drawer-open behavior (no toggle-close regression).
- `More Weather Settings` action is disabled with explicit reason when drawer is blocked by task-edit mode.
- `More Weather Settings` behavior is drawer-first in V1; direct weather deep-link is not required in this slice.
- `Weather` tab includes drawer-first helper copy that references current labels: `Settings -> General -> RainViewer`.
- When navdrawer `Settings` is collapsed, `More Weather Settings` flow expands it so `General` remains discoverable.
- `More Weather Settings` action uses a dedicated explicit-open drawer request path (no shared hamburger-toggle callback reuse).
- In blocked mode, hamburger toggle path cannot reopen drawer from closed state.
- Discoverability-driven `Settings` expansion is transient by default and does not persist as user preference mutation unless explicitly changed by user.
- Task-panel conflict policy uses non-hidden semantics (`COLLAPSED` and expanded states treated as panel-visible for arbitration).
- Weather advanced flow awaits sheet-dismiss completion barrier before dispatching drawer-open command.
- `SkySight` tab includes:
  - `Thermal Tops`
  - `Convergence`
  - `Satellite View`
- `Thermal Tops` maps to `dwcrit`; `Convergence` maps to `wblmaxmin`.
- `Satellite View` is disabled/coming-soon (`3B`) and does not mutate map style in this slice.
- SkySight controls preserve existing forecast primary-selection semantics.
- SkySight activation policy is implemented; options are not dead when primary overlays start disabled.
- Modal coexistence contract is enforced (task panel, detail sheets, airspace modal).
- Single-modal contract is enforced (only one modal sheet visible at a time).
- Legacy forecast FAB/detail paths also satisfy single-modal arbitration while dual-entry remains.
- Legacy forecast FAB path does not open forecast modal while task panel is visible.
- Bottom strip respects navigation-bar insets.
- No map tap/long-press click-through from tab/scrim interactions.
- Bottom-end action lane remains usable in replay/AAT/debug permutations.
- Existing navdrawer Weather settings remain available unless parity-gated consolidation is explicitly delivered.
- Existing forecast advanced controls remain available unless parity-gated Phase 3 is explicitly delivered.
- No business/domain logic added to UI layer.
- No new duplicate SSOT owners introduced.
- `enforceRules`, `testDebugUnitTest`, and `assembleDebug` pass.
- No guarded-file line budget regressions.
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved.

## 8) Rollback Plan

- Revert only new bottom-tab-sheet files and integration callsites.
- If Phase 3 is applied, restore previous direct forecast sheet trigger path.
- Re-run required checks to confirm pre-feature behavior.
