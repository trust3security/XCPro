# 2Tab.md

## Purpose

Define and track implementation for the 2nd bottom tab (`SkySight`) as the primary control surface for forecast overlays.

Date: 2026-02-23  
Status: Implemented (tab-hosted controls)

## Implemented Scope

1. Bottom tab names remain:
- Tab 1: `Weather`
- Tab 2: `SkySight`
- Tab 3: `Scia`
- Tab 4: placeholder (`Tab 4`)

2. SkySight tab now hosts in-flight overlay controls:
- non-wind overlay enable + single-parameter selection
- wind overlay enable + wind parameter selection
- `Sat View` toggle (switches map to `Satellite` style while enabled)
- SkySight satellite API overlay controls:
  - enable/disable satellite overlays
  - imagery (clouds) toggle
  - radar toggle
  - lightning toggle
  - animation toggle
  - history frames (1-6, 10-minute stepping)
- time selection, auto-time, and follow-time offset
- legends, loading state, warning, and error messaging

3. General SkySight settings own:
- opacity
- wind display mode (Arrow/Barb)
- wind marker size
4. The MapScreen forecast FAB shortcut was removed.
5. SkySight controls are available only in the bottom tabs sheet (`SkySight` tab), keeping one control surface.

## Current Product Behavior

1. SkySight options are managed from the bottom tabs sheet (`SkySight` tab).
2. SkySight warning/error messages are visible inside the tab, including map-center region coverage warnings.
3. Forecast overlay runtime rendering still follows existing forecast SSOT and overlay manager wiring.
4. `Sat View` toggle remains transient map-style behavior from SkySight tab:
- On enable: apply `Satellite` style.
- On disable: restore last non-satellite style (fallback `Topo`).
5. SkySight satellite API overlays are rendered through dedicated runtime overlay wiring
   (`MapOverlayManager` -> `SkySightSatelliteOverlay`) and can run in parallel with forecast/wind overlays.
6. When SkySight satellite overlays are active, OGN glider markers use a white-contrast icon mode
   for map readability. This switch is applied lazily on each target's next normal OGN update.
7. Dual-rain arbitration is active:
   - when RainViewer is enabled and SkySight non-wind parameter is `Rain` (`accrain`),
     SkySight primary rain rendering is suppressed to avoid dual rain layers,
     while SkySight wind overlay remains available.
8. Satellite animation frame order is oldest -> newest and loops predictably (`1,2,3,4,5,6,1,2,...`)
   so cloud/radar/lightning movement direction remains visually coherent.

## Architecture and SSOT

1. Forecast SSOT owner remains unchanged:
- `ForecastOverlayRepository` (via use-cases + `ForecastOverlayViewModel`)

2. UI composition:
- `MapBottomSheetTabs.kt` renders tab shell and hosts SkySight controls content.
- `ForecastOverlayBottomSheet.kt` provides reusable `ForecastOverlayControlsContent` used by tab and any modal host path.

3. Intent flow (UDF):
- UI tab controls -> `ForecastOverlayViewModel` intents -> forecast use-cases -> forecast repository SSOT

## Implemented File Touchpoints

1. `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
2. `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt`
3. `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
4. `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt`
5. `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
6. `feature/map/src/main/java/com/example/xcpro/map/MapScreenState.kt`
7. `feature/map/src/main/java/com/example/xcpro/map/MapLifecycleManager.kt`

## Verification

1. `./gradlew.bat :feature:map:compileDebugKotlin --no-configuration-cache`
- PASS (2026-02-23)

## Next Optional Enhancements

1. Add focused Compose/UI tests for SkySight tab control rendering and interactions.
2. If modal forecast sheet is fully retired, remove unused modal entrypoints after product confirmation.
