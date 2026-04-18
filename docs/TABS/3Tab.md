# 3Tab.md

## Purpose

Define and track implementation for the 3rd bottom tab (`Scia`) backed by OGN data, with selective per-aircraft glider trail visibility.

Date: 2026-02-22  
Status: Implemented (v1, no cap)

## Implemented Scope

1. Bottom tab names are:
- Tab 1: `Weather`
- Tab 2: `SkySight`
- Tab 3: `Scia`
- Tab 4: placeholder (`Tab 4`)

2. OGN tab content is live:
- `OGN Traffic` switch (parity with OGN FAB path)
- OGN aircraft list with per-aircraft trail toggles

3. Per-aircraft trail filtering is live:
- Raw OGN trail segments are filtered by selected aircraft keys.
- Overlay obeys `ognOverlayEnabled` and aircraft selection filter only.

## Current Product Behavior (v1)

1. OGN trail selection is persisted via DataStore.
2. Aircraft list labels use registration-first display fallback:
- registration
- competition number
- callsign
- display label
- target key
3. No hard cap is enforced on selected aircraft trails (user chose no-cap path).
4. Default for unseen aircraft is OFF (not selected) until user enables that row.

## Architecture and SSOT

1. Existing SSOT owners unchanged:
- OGN overlay enabled: `OgnTrafficPreferencesRepository`

2. New SSOT owner:
- `OgnTrailSelectionPreferencesRepository`
- Persisted key set: `ogn_trail_selected_aircraft_keys`

3. ViewModel/use-case wiring:
- `OgnTrailSelectionUseCase`
- `OgnTrailSelectionViewModel`

## Implemented File Touchpoints

1. `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabs.kt`
2. `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
3. `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindings.kt`
4. `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrailSelectionPreferencesRepository.kt`
5. `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrailSelectionUseCase.kt`
6. `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrailSelectionViewModel.kt`

## Verified Gates

1. `./gradlew --no-configuration-cache enforceRules`
2. `./gradlew --no-configuration-cache testDebugUnitTest`
3. `./gradlew --no-configuration-cache assembleDebug`

All passed on 2026-02-22.

## Next Optional Enhancements

1. Add stale-key pruning for persisted aircraft keys when target IDs permanently disappear.
2. Add explicit unit tests for aircraft-row label mapping and per-aircraft trail filter behavior.
3. If product later wants a cap, define deterministic overflow policy before implementation.
