# SCIA System Map And Current Behavior

Date
- 2026-02-27

Status
- Current implementation snapshot.

## 1) Definition

In this codebase, "SCIA" refers to OGN glider trail/wake rendering (not ownship snail trail).

Primary runtime classes:
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnGliderTrailRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/map/OgnGliderTrailOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`

## 2) End-to-End Flow (Toggle To Pixels)

UI to persistence:
- SCIA switch in bottom tab: `MapBottomSheetTabs.kt` (`Show Scia`).
- UI intent routing: `MapScreenContent.kt` -> `onToggleOgnScia()`.
- ViewModel forwarding: `MapScreenViewModel.kt:313`.
- Coordinator mutation: `MapScreenTrafficCoordinator.kt:247`.
- Persistence write: `MapScreenUseCases.kt:326-327` -> `OgnTrafficPreferencesRepository.setShowSciaEnabled(...)` (`dataStore.edit` at `OgnTrafficPreferencesRepository.kt:147-149`).

Persistence to render:
- SCIA preference flow: `OgnTrafficPreferencesRepository.showSciaEnabledFlow`.
- App-start reset: `XCProApplication.onCreate()` forces SCIA OFF and clears selected trail-aircraft keys on fresh process launch.
- ViewModel state: `MapScreenViewModel.showOgnSciaEnabled`.
- Bindings: `MapScreenBindings.kt` and selected-aircraft filtering (`MapScreenBindings.kt:94-109`).
- Map effect gate: only render trails when `ognOverlayEnabled && showOgnSciaEnabled` in `MapScreenRootEffects.kt:84`.
- Runtime handoff: `MapScreenRootEffects.kt:103-104` -> `MapOverlayManager.updateOgnGliderTrailSegments(...)`.
- Overlay render: `OgnGliderTrailOverlay.render(...)`.

## 3) What Is Displayed When SCIA Is Enabled

High-level behavior:
- Turning SCIA on forces OGN traffic on if needed (`MapScreenTrafficCoordinator.kt:254`).
- An additional guard auto-enables OGN if SCIA is true while OGN is false (`MapScreenTrafficCoordinator.kt:193-202`).
- While SCIA is enabled, direct OGN toggle from tab is disabled (`MapBottomSheetTabs.kt:353`).

Trail visibility behavior:
- Trails render only for selected OGN aircraft keys.
- If no aircraft keys are selected, the map receives `emptyList()` trails (`MapScreenBindings.kt:101-105`).
- Selection is SSOT in `OgnTrailSelectionPreferencesRepository`; default is empty set (`OgnTrailSelectionViewModel.kt:21`) and is reset to empty on app restart.
- User can select per-aircraft trail visibility:
  - In OGN tab rows (`MapBottomSheetTabs.kt`).
  - In marker detail sheet (`OgnMarkerDetailsSheet.kt:53-59`, wired in `MapScreenContent.kt:732-741`).

User-facing tab copy:
- Tab label: `Scia` and header `Scia (trail/wake)` (`MapBottomSheetTabs.kt:340`).
- Prompt when disabled: `Enable Show Scia to display OGN trails/wake.` (`MapBottomSheetTabs.kt:374`).
- Empty-state prompt: `No OGN aircraft currently available.` (`MapBottomSheetTabs.kt:379`).

## 4) Render Characteristics

Data volume bounds:
- Repository retention cap: `MAX_SEGMENTS = 24_000` (`OgnGliderTrailRepository.kt:333`).
- Map render cap: `MAX_RENDER_SEGMENTS = 12_000` newest segments (`OgnGliderTrailOverlay.kt:149,153`).

Layer behavior:
- SCIA is rendered as line geometry in `OgnGliderTrailOverlay`.
- Overlay initializes below thermal/icon/blue-location layers when present.

Display cadence behavior:
- OGN display update mode controls render cadence only:
  - `REAL_TIME` = 0 ms (no throttle)
  - `BALANCED` = 1000 ms
  - `BATTERY` = 3000 ms
- Source: `OgnDisplayUpdateMode.kt:12-25`.

## 5) Current Failure Safety

Preference mutation failures:
- Wrapped in coordinator mutation guards with UI toast fallback:
  - `runPreferenceMutation(...)` in `MapScreenTrafficCoordinator.kt:345-353`.

Trail render failures:
- Guarded at manager level with `runCatching`:
  - `MapOverlayManager.renderOgnTrailsNow()` at `MapOverlayManager.kt:375-383`.
- Guarded at overlay level with `try/catch` and clear-on-failure fallback:
  - `OgnGliderTrailOverlay.kt:58-90`.

## 6) Known User Symptom

Observed symptom:
- Tap SCIA on/off can feel paused before UI state settles.

Most likely causes in current flow:
- DataStore write/roundtrip before switch state reflects persisted truth.
- Immediate heavy trail feature build during first render burst when many segments exist.
- Optional intentional delay when display mode is `BALANCED` or `BATTERY`.

Additional implementation notes from second pass:
- Trail selection filtering currently executes in Compose binding path:
  - `MapScreenBindings.kt:94-107`.
- SCIA enable from OGN-off path currently performs two sequential preference writes:
  - `MapScreenTrafficCoordinator.kt:254` then `:256`.
- Render throttle cadence can delay first repaint because trail updates default to non-forced render:
  - `MapOverlayManager.kt:343,349,393`.
- Trail segments can remain retained while streaming is disabled and only expire by history window:
  - `OgnGliderTrailRepository.kt:97-99`, retention window at `:332`.

Additional implementation notes from third pass:
- SCIA toggle writes are launched per tap with no in-flight coalescing:
  - `MapScreenTrafficCoordinator.kt:331-337`.
- Marker details SCIA enable path can trigger multi-source writes:
  - trail selection write (`MapScreenContent.kt:733`) plus SCIA/OGN toggles (`MapScreenContent.kt:738-741`).
- Large-list compare/key cost exists in hot render wiring:
  - manager list equality check (`MapOverlayManager.kt:344-345`),
  - compose effect keyed by full trails list (`MapScreenRootEffects.kt:84,103`).
- Startup path can push trails twice:
  - map-ready immediate push (`MapScreenScaffoldInputs.kt:207-210`),
  - then overlay effect push (`MapScreenRootEffects.kt:103-104`).
- Trail selection state starts empty before DataStore emission:
  - `OgnTrailSelectionViewModel.kt:21`.
  - App startup reset also clears persisted selected keys on fresh process launch.
- Trail-selection flow is collected in two UI locations:
  - `MapScreenBindings.kt` and `MapScreenContent.kt`.

Additional implementation notes from fourth pass:
- `MapViewHost` invokes `onMapReady` before and after `initializeMap(...)`:
  - first callback before map initialization (`MapScreenSections.kt:221-225`),
  - second callback after initialization (`MapScreenSections.kt:240-244`).
- `onMapReady` callback currently pushes forced immediate OGN/SCIA overlay updates:
  - `MapScreenScaffoldInputs.kt:195-210`.
- Combined effect:
  - startup can trigger extra heavy SCIA trail push work in addition to normal overlay effects.
- Key-match helper does per-call selected-set normalization:
  - `selectionSetContainsOgnKey(...)` rebuilds normalized selected set on every call (`OgnAddressing.kt:84-85`).
- Current SCIA segment filtering calls that helper per segment in Compose binding path:
  - `MapScreenBindings.kt:97-109`,
  - increasing CPU cost in dense sessions.
