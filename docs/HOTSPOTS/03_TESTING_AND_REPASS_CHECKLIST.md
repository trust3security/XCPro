# HOTSPOTS Testing and Re-pass Checklist

This checklist is for final validation before merge.

## 1) Unit tests required

## 1.1 Thermal repository tests

File: `feature/map/src/test/java/com/example/xcpro/ogn/OgnThermalRepositoryTest.kt`

Add or update tests for:

- turn gate rejects thermal when cumulative turn is `<= 730`
- turn gate accepts thermal when cumulative turn is `> 730`
- retention `1h` drops old hotspots and keeps recent ones
- retention `all day` keeps same-day hotspots and drops prior-day hotspots at local midnight
- area dedupe selects one hotspot per local area and prefers active/recent winner before strength tie-breaks
- display-percent filter keeps only strongest top `N%` hotspots (5..100)
- confirmed-tracker missing hotspot ID does not crash and self-recovers stable hotspot ID

Add or update UI/runtime tests for:

- hotspot toggle-on behavior when OGN overlay is currently off
- thermal visibility when forecast/weather/satellite overlays are active together

## 1.2 Preferences tests

File: `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`

Add or update tests for:

- retention default equals all-day setting
- retention clamping at min and max bounds
- retention persistence round-trip
- display-percent default equals `100`
- display-percent clamping at min `5` and max `100`
- display-percent persistence round-trip

## 2) Manual QA script

1. Open `Settings -> General -> Hotspots`.
2. Verify modal/open behavior matches product intent.
3. Move slider to `1 hour`, return to map, confirm older hotspots disappear.
4. Move slider to `All day`, confirm same-day hotspots remain.
5. Move slider to `100%`, confirm all retained hotspots are visible.
6. Move slider to `5%`, confirm only the strongest top-share hotspots remain.
7. In dense glider traffic area, confirm only one hotspot is shown per area.
8. Verify non-circling climbs do not produce hotspot confirmations.
9. Turn OGN overlay off, then turn Hotspots on; verify expected contract (auto-enable OGN or disabled thermal toggle).
10. Enable forecast + weather rain + satellite overlays and verify hotspots remain visible.

## 3) Architecture/doc sync

- Update `docs/ARCHITECTURE/PIPELINE.md` thermal section.
- Update `docs/OGN/OGN.md` lifecycle details.
- Keep `docs/HOTSPOTS` docs aligned with final constants and behavior.

## 4) Command checklist

```bat
./gradlew.bat enforceRules
./gradlew.bat :feature:map:testDebugUnitTest
./gradlew.bat assembleDebug
```

Optional when environment is available:

```bat
./gradlew.bat connectedDebugAndroidTest --no-parallel
```

## 5) Re-pass process (3 passes)

Run exactly three focused passes before final signoff:

1. Pass 1: runtime policy correctness
   - repository wiring
   - retention logic
   - turn gate
   - area dedupe
   - display-percent top-share filtering
2. Pass 2: UI and navigation correctness
   - General button
   - Hotspots settings UX contract
   - retention + display-percent slider persistence behavior
3. Pass 3: tests and docs correctness
   - unit coverage for all new policy branches
   - pipeline/docs sync complete

Log each pass outcome with file and line references in review notes.

## 6) Re-pass Log (2026-02-25)

Pass 1 outcome:

- Finding: Hotspots can be toggled on while OGN overlay remains off.
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt:214`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt:89`
- Finding: Thermal layers can be occluded by other overlays due to anchor ordering.
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:777`
  - `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt:311`
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt:350`
- Finding: `OgnThermalsButton` appears unused.
  - `feature/map/src/main/java/com/example/xcpro/map/components/OgnThermalsButton.kt:16`

Pass 2 outcome:

- Confirmed: thermal toggle contract differs from SCIA contract and has no guard when OGN is off.
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt:214`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt:224`
- Confirmed: thermal switch is always interactive in Tab 4 and does not reflect OGN-off disablement/auto-enable contract.
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt:437`
- Coverage gap: no dedicated coordinator-level test asserting hotspot toggle behavior when OGN overlay is off.

Pass 3 outcome:

- Confirmed: thermal layer IDs are not present in forecast/weather/satellite anchor lists.
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:777`
  - `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt:311`
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt:350`
- Risk remains: thermal circles/labels can be visually occluded when other overlays are inserted later in style order.

Pass 4 outcome:

- Targeted tests pass:
  - `com.example.xcpro.ogn.OgnThermalRepositoryTest`
  - `com.example.xcpro.map.ui.MapBottomSheetTabsTest`
  - `com.example.xcpro.screens.navdrawer.GeneralSettingsScreenPolicyTest`
- Confirmed low-severity cleanup item remains:
  - `OgnThermalsButton` appears unused in active map UI composition paths.
  - `feature/map/src/main/java/com/example/xcpro/map/components/OgnThermalsButton.kt:16`

## 7) Re-pass Log Cycle 2 (2026-02-25)

Pass 1 (runtime policy) outcome:

- Reconfirmed: repository-side detection/filtering path remains stable (`>730` turn gate, area dedupe, top-share filter).
- No new runtime policy defects observed.

Pass 2 (UI/navigation) outcome:

- Reconfirmed: hotspot toggle can be enabled while OGN overlay remains off.
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt:214`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt:89`

Pass 3 (overlay z-order) outcome:

- Reconfirmed: thermal layer IDs remain absent from forecast/weather/satellite anchor lists.
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:777`
  - `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt:311`
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt:350`

Pass 4 (tests/docs) outcome:

- Targeted tests completed successfully after rerun:
  - `com.example.xcpro.ogn.OgnThermalRepositoryTest`
  - `com.example.xcpro.map.ui.MapBottomSheetTabsTest`
  - `com.example.xcpro.screens.navdrawer.GeneralSettingsScreenPolicyTest`
- First run transiently failed with build artifact lock in `transformDebugClassesWithAsm`; second run passed unchanged.

## 8) Implementation status update (2026-02-25)

Resolved from re-pass findings:

- Thermal toggle now auto-enables OGN overlay when turning on:
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
- Thermal layers are now included in forecast/weather/satellite anchor lists:
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt`

Added regression coverage:

- `feature/map/src/test/java/com/example/xcpro/map/HotspotsOverlayPolicyTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt` (`onToggleOgnThermals_enablingForcesOgnTrafficOn`)

## 9) Crash-hardening status update (2026-02-26)

Resolved:

- Repository confirmed-tracker hotspot ID path now self-recovers instead of failing with a hard invariant crash.
- Thermal overlay render path now skips invalid coordinates and guards source updates.
- Overlay manager thermal render path is wrapped with runtime error guard logging.

Added regression coverage:

- `feature/map/src/test/java/com/example/xcpro/ogn/OgnThermalRepositoryTest.kt`
  (`recoversWhenConfirmedTrackerHotspotIdIsMissing`)
