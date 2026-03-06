# OGN Target System Map and Current Behavior

Date
- 2026-03-06

Purpose
- Document current OGN tap/details/overlay behavior and identify exact integration points for a new Target feature.

## 1) Current user flow (implemented today)

1. User taps an OGN marker on map.
2. Tap routing resolves in `MapOverlayStack` via `overlayManager.findOgnTargetAt(...)`.
3. `MapScreenViewModel.onOgnTargetSelected(id)` updates selected OGN details state.
4. `MapOverlayPanelsAndSheetsSection` shows `OgnMarkerDetailsSheet`.
5. Bottom sheet currently supports only `Show Scia for this aircraft` toggle (trail visibility).

Primary files
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSections.kt`
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnMarkerDetailsSheet.kt`

## 2) Current OGN overlay runtime path

Current overlays (UI runtime owners)
- OGN traffic markers/labels: `OgnTrafficOverlay`
- OGN thermals: `OgnThermalOverlay`
- OGN trails (SCIA): `OgnGliderTrailOverlay`

Runtime owner
- `MapOverlayManagerRuntime` delegates OGN rendering to `MapOverlayManagerRuntimeOgnDelegate`.

Data ingress to runtime
- `MapScreenRoot.kt` -> `MapScreenOverlayEffects(...)` -> `overlayManager.updateOgnTrafficTargets(...)` and related methods.

Primary files
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/OgnGliderTrailOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`

## 3) Current preference/state ownership relevant to Target

Existing OGN prefs SSOT
- `OgnTrafficPreferencesRepository` (overlay enabled, icon size, Scia, thermals, radius, etc.)

Existing per-aircraft selection SSOT
- `OgnTrailSelectionPreferencesRepository` for SCIA trail aircraft keys.

Missing today
- No SSOT field for "targeted OGN aircraft key".
- No runtime overlay for ownship->target direct line.
- No targeted-marker visual treatment in `OgnTrafficOverlay`.

## 4) Integration points for new Target feature

Bottom sheet
- Extend `OgnMarkerDetailsSheet` with a second switch:
  - `Target this aircraft`.

State wiring
- Add target selection flow(s) to OGN preference/use-case layer.
- Expose target state via `MapScreenViewModel` similarly to other OGN state.

Rendering
- Marker target ring: extend `OgnTrafficOverlay` feature/layer styling.
- Direct line: add dedicated runtime overlay class and delegate wiring.

## 5) Constraints from architecture and pipeline

- Keep SSOT for target state in repository/use-case flow, not local Compose state.
- Keep map overlay objects inside UI runtime (`MapOverlayManager*`), not ViewModel.
- Keep tap-routing policy in map gesture layer (`MapOverlayStack`) unchanged except target actions.
- Keep display-only behavior visual; do not mutate OGN repository target data for rendering effects.
