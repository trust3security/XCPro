# Forecast Controls Extraction Execution Brief 2026-03-12

## Purpose

Define the first release-grade, low-churn implementation slice for reducing
local edit-build cost without broad refactors or behavior risk.

This brief is intentionally narrow. It is a pilot extraction for forecast UI
controls only. It is not a general forecast refactor.

## Why This First

Current evidence still shows the dominant hotspot is `:feature:map:compileDebugKotlin`.
However, the last safe settings-screen moves did not improve the benchmark.

The next slice should therefore meet all of these constraints:

- low behavior risk
- low dependency churn
- no DI churn
- no SSOT movement
- useful as a repeatable extraction pattern

`ForecastOverlayBottomSheetControls.kt` is a good first slice because it is:

- large (`486` lines)
- mostly pure Compose UI
- already driven by forecast-owned models
- currently compiled inside `feature:map`

## Scope

### In scope

- move forecast overlay controls UI out of `feature:map`
- move the forecast-only formatting helpers used by that controls UI
- keep `feature:map` as a thin sheet launcher and tab wiring layer

### Out of scope

- no changes to overlay rendering behavior
- no changes to map overlay manager behavior
- no changes to repositories, use cases, or DI modules
- no package renames unless strictly required
- no string/content changes visible to users
- no benchmark-script changes
- no line-budget cleanup unrelated to this slice

## SSOT and Architecture Gate

### SSOT ownership

- Forecast settings SSOT remains with forecast/profile-owned repositories.
- `feature:map` must not become the owner of any moved forecast state.
- The moved UI must remain a pure renderer of passed-in state and callbacks.

### Dependency direction

- UI -> domain/data state only
- no new `feature:profile -> feature:map` dependency is allowed
- `feature:map` may depend on the moved forecast UI surface
- the moved forecast UI surface must not depend on map-only runtime classes

### Time base

- no time-source behavior changes are allowed in this slice
- existing time formatting behavior must remain unchanged
- helper relocation must preserve current `forecastRegionZoneId(...)` usage

### Replay determinism

- unchanged
- this slice must not alter runtime data selection, timing, or replay state

## Files

### Source to extract from `feature:map`

- [ForecastOverlayBottomSheetControls.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheetControls.kt)

### Thin map-side wrappers to keep

- [ForecastOverlayBottomSheetRuntime.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheetRuntime.kt)
- [MapBottomSheetTabs.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt)

### Forecast-owned models already outside `feature:map`

- [ForecastOverlayModels.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/profile/src/main/java/com/example/xcpro/forecast/ForecastOverlayModels.kt)
- [ForecastSettings.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/profile/src/main/java/com/example/xcpro/forecast/ForecastSettings.kt)

## Target Shape

Create a new forecast-owned UI file in `feature:profile` and move only the pure
controls surface into it.

Recommended target files:

- `feature/profile/src/main/java/com/example/xcpro/forecast/ui/ForecastOverlayControlsContent.kt`
- `feature/profile/src/main/java/com/example/xcpro/forecast/ui/ForecastOverlayFormatting.kt`

Keep the package name stable if that avoids a wider import churn wave.

## Exact Move Boundary

### Move

- `ForecastOverlayControlsContent(...)`
- `formatForecastTime(...)`
- `formatFollowTimeOffsetLabel(...)`

### Keep in `feature:map`

- `ForecastOverlayBottomSheet(...)`
- `ForecastPointCalloutCard(...)`
- `ForecastQueryStatusChip(...)`
- map bottom-sheet tab selection and tab wiring

### Do not move in this slice

- `ForecastOverlayRuntime.kt`
- `MapOverlayManagerRuntimeForecastWeatherDelegate.kt`
- `SkySightForecastProviderAdapter.kt`
- forecast repositories
- forecast Hilt wiring

## Implementation Steps

1. Add the new forecast-owned UI file under `feature:profile`.
2. Move `ForecastOverlayControlsContent(...)` into that file with no behavioral edits.
3. Move `formatForecastTime(...)` and `formatFollowTimeOffsetLabel(...)` into a
   small forecast-owned helper file.
4. Update [ForecastOverlayBottomSheetRuntime.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheetRuntime.kt) to import and call the moved composable/helpers.
5. Update [MapBottomSheetTabs.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt) to import the moved composable.
6. Delete the moved declarations from the original map file.
7. Stop. Do not extend the slice into forecast runtime or weather runtime work.

## Non-Negotiable Guardrails

- No public behavior changes.
- No DI changes.
- No new module.
- No changes to app navigation.
- No test rewrites unless required by moved imports.
- No extra cleanup in neighboring files.
- No "while here" refactors.

## Verification

Run serially on this Windows machine:

```bat
./gradlew.bat :feature:profile:compileDebugKotlin :feature:map:compileDebugKotlin :app:assembleDebug --console=plain --no-daemon
```

If relevant tests fail to compile because of import movement, run:

```bat
./gradlew.bat :feature:profile:compileDebugUnitTestKotlin :feature:map:compileDebugUnitTestKotlin --console=plain --no-daemon
```

Do not run parallel Gradle graphs during this work.

## Benchmark

This slice is intended to reduce the cost of editing forecast-controls UI by
moving that edit surface out of `feature:map`.

Important:

- do not treat `map-impl` as the only success metric for this slice
- a global `map-impl` win is possible but not guaranteed
- the primary success condition is that edits to forecast controls no longer
  require recompiling the full `feature:map` controls implementation

Minimum post-change check:

```powershell
./gradlew.bat :app:compileDebugKotlin --profile --console=plain
```

If the profiled compile path is neutral or slightly worse relative to the
retained historical `map-impl` baseline, but the moved controls now compile
through `feature:profile`, that is acceptable for this pilot. Do not continue
blindly into larger forecast work without reviewing the result.

## Stop Conditions

Stop immediately if any of these happen:

- the moved UI requires map-only runtime types
- the move introduces a `feature:profile -> feature:map` dependency need
- the move starts pulling overlay-manager logic into the forecast-owned UI file
- compile failures require unrelated forecast/runtime rewrites
- the diff starts expanding beyond the files listed in this brief

## Rollback Condition

Rollback this slice if:

- behavior changes are needed to make the move compile
- the move forces DI churn
- the move grows into forecast runtime extraction instead of pure controls UI

## Expected Payoff

Expected payoff is moderate, not dramatic.

What this slice should achieve:

- establish a safe extraction pattern
- reduce `feature:map` ownership of forecast-only UI
- lower rebuild cost for forecast-controls edits

What this slice probably will not achieve alone:

- full resolution of the `:feature:map:compileDebugKotlin` hotspot
- meaningful reduction of `:feature:map:kspDebugKotlin`
- meaningful reduction of `:app:kspDebugKotlin`

## Next Step After This Slice

Only if this slice lands cleanly and stays low-churn:

1. review the benchmark result
2. then decide whether to extract the next forecast/weather runtime layer:
   `ForecastOverlayRuntime.kt` or
   `MapOverlayManagerRuntimeForecastWeatherDelegate.kt`

Do not start that next slice in the same change.
