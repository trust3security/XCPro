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

2. SkySight tab now hosts the full forecast controls previously shown in the separate forecast FAB sheet:
- non-wind overlays enable/toggle
- wind overlay enable + wind parameter selection
- time selection, auto-time, and follow-time offset
- opacity
- wind marker display mode + size
- legends, loading state, warning, and error messaging

3. The MapScreen forecast FAB shortcut was removed.
4. SkySight controls are available only in the bottom tabs sheet (`SkySight` tab), keeping one control surface.

## Current Product Behavior

1. SkySight options are managed from the bottom tabs sheet (`SkySight` tab).
2. SkySight warning/error messages are visible inside the tab, including map-center region coverage warnings.
3. Forecast overlay runtime rendering still follows existing forecast SSOT and overlay manager wiring.

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
4. `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentOverlays.kt`
5. `feature/map/src/main/java/com/example/xcpro/map/components/MapActionButtons.kt`
6. `feature/map/src/main/java/com/example/xcpro/map/components/MapActionButtonItems.kt`

## Verification

1. `./gradlew.bat :feature:map:compileDebugKotlin --no-configuration-cache`
- PASS (2026-02-23)

## Next Optional Enhancements

1. Add focused Compose/UI tests for SkySight tab control rendering and interactions.
2. If modal forecast sheet is fully retired, remove unused modal entrypoints after product confirmation.
