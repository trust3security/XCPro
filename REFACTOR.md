# Refactor Plan: Map UDF/SSOT Compliance

## Purpose
Move map UI state mutations out of composables and into a ViewModel-owned state store, while
keeping map runtime actions (MapLibre/MapView) fast and imperative. This aligns with ARCHITECTURE.md
and CODING_RULES.md without regressing gesture performance.

## Scope
- feature/map map UI + state pipeline
- MapScreenState replacement/refactor
- MapScreenViewModel event/command flow
- Map UI composables to render-only

## Invariants (Must Hold)
- UI renders state only (no direct state mutation).
- All authoritative map state lives in a single store (SSOT).
- ViewModel does not reference UI/platform types (MapView/MapLibreMap).
- High-frequency map actions remain imperative and do not trigger excessive recomposition.

## Plan (Incremental)
1) Introduce MapStateStore (SSOT) and MapCommand flow.
2) Create MapRuntimeController (UI-only) to apply MapCommand to MapView/MapLibreMap.
3) Migrate low-risk state first: safeContainerSize, mapStyleUrl.
4) Migrate tracking UI flags: showReturnButton, showRecenterButton, isTrackingLocation.
5) Migrate flight mode state: currentMode/currentFlightMode.
6) Migrate camera/target fields: targetLatLng/zoom/savedLocation/zoom/bearing.
7) Reduce/remove MapScreenState, leaving only UI/runtime cache if needed.
8) Add tests for MapStateStore + ViewModel intents.

## Risks
- Gesture regressions if high-frequency updates enter UI state flow.
- Map runtime lifecycle bugs if MapView ownership moves incorrectly.

## Progress Log
- 2026-01-02: Plan created; ready to start Step 1.
- 2026-01-02: Step 1 started/complete: added MapStateStore + MapCommand, wired mapCommands flow in MapScreenViewModel.
- 2026-01-02: Step 3 complete: safeContainerSize + map style now sourced from MapStateStore; MapInitializer reads mapStyleName.
- 2026-01-02: Step 4 complete: tracking flags moved to MapStateStore; UI no longer mutates recenter/return/tracking flags directly.
- 2026-01-02: Step 5 complete: flight mode state now flows through MapStateStore + ViewModel setFlightMode; UI uses callbacks.
- 2026-01-02: Step 6 complete: currentZoom + targetLatLng/targetZoom now sourced from MapStateStore; MapCameraEffects consumes store flows.
- 2026-01-02: Step 7 complete: runtime MapScreenState + map managers moved to UI; ViewModel no longer owns MapView/MapLibreMap.
- 2026-01-03: Step 8 complete: added MapStateStore + ViewModel intent tests; distance circle toggle now stored in MapStateStore.
