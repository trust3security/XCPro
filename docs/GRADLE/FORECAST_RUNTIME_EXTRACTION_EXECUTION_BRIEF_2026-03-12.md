# Forecast Runtime Extraction Execution Brief 2026-03-12

## Purpose

Define the second release-grade, low-churn build-speed slice after the forecast
controls extraction.

This slice targets the forecast runtime engine, not forecast UI, not weather
rendering, and not DI restructuring.

## Why This Slice

The first forecast-controls slice was structurally correct and low risk, but it
only produced a small `map-impl` improvement. The remaining hotspot is still
inside `feature:map`, and the next best forecast-owned candidate is:

- [ForecastOverlayRuntime.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRuntime.kt)

Why this file is a good next target:

- `475` lines
- no Compose UI
- no map UI types
- no overlay-manager types
- depends on forecast-owned models/preferences plus narrow ports

This makes it a stronger build-surface candidate than more settings-screen work.

## Scope

### In scope

- move the forecast runtime engine out of `feature:map`
- move the runtime support file it directly depends on
- move the forecast port interfaces that the runtime depends on
- keep package names stable to minimize import churn

### Out of scope

- no forecast UI moves in this slice
- no repository ownership changes
- no ViewModel moves
- no Hilt module restructuring
- no weather runtime extraction
- no overlay-manager changes
- no benchmark-script changes

## Target Files

### Move from `feature:map` to `feature:profile`

- [ForecastOverlayRuntime.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRuntime.kt)
- [ForecastOverlayRuntimeSelectionSupport.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRuntimeSelectionSupport.kt)
- [ForecastPorts.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastPorts.kt)

### Keep in `feature:map`

- [ForecastOverlayRepository.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRepository.kt)
- [ForecastOverlayUseCases.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayUseCases.kt)
- [ForecastOverlayViewModel.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayViewModel.kt)
- [SkySightForecastProviderAdapter.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/SkySightForecastProviderAdapter.kt)
- [FakeForecastProviderAdapter.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/FakeForecastProviderAdapter.kt)
- [ForecastModule.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/di/ForecastModule.kt)

## Boundary Rules

### Keep package stable

Use the existing package:

- `com.trust3.xcpro.forecast`

This is important. It avoids a rename wave through repository, use cases, DI,
tests, and adapters.

### Keep the repository as the map-side facade

The repository remains in `feature:map` for this slice.

That means:

- `ForecastOverlayRepository` still constructs the runtime
- `ForecastOverlayUseCases` still depend on the repository
- `ForecastOverlayViewModel` stays unchanged

This keeps the slice bounded and avoids a larger Hilt churn wave.

### Keep ports as narrow contracts

Move the port interfaces with the runtime so `feature:profile` does not need to
depend on `feature:map`.

Map-side adapters such as `SkySightForecastProviderAdapter` remain where they
are and continue implementing the moved interfaces.

## Architecture Gate

### SSOT ownership

- SSOT remains in [ForecastPreferencesRepository.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/profile/src/main/java/com/trust3/xcpro/forecast/ForecastPreferencesRepository.kt)
- the moved runtime remains a pure state-assembly/runtime engine
- do not move repository state ownership in this slice

### Dependency direction

- `feature:profile` must not gain a dependency on `feature:map`
- `feature:map` may continue depending on `feature:profile`
- the moved runtime/support/ports must not import map-only classes

### Time base

- keep the existing injected [Clock.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/core/time/src/main/java/com/trust3/xcpro/core/time/Clock.kt) behavior unchanged
- no direct wall-time calls may be introduced

### Replay determinism

- unchanged
- this slice must not alter forecast slot selection or retry policy behavior

## Expected Diff Shape

The intended diff should be narrow:

- file moves
- import adjustments if needed
- no logic rewrite
- no algorithm change
- no behavior change

If the diff starts adding conditionals, new state, or new configuration flags,
the slice has drifted and should stop.

## Implementation Steps

1. Physically move the three target files into `feature/profile/src/main/java/com/trust3/xcpro/forecast/`.
2. Keep the package declaration unchanged as `com.trust3.xcpro.forecast`.
3. Delete the originals from `feature:map`.
4. Compile `feature:profile`.
5. Compile `feature:map`.
6. Fix only narrow import/visibility fallout.
7. Stop.

## What Should Not Need to Change

These should remain unchanged or nearly unchanged:

- constructor shape in [ForecastOverlayRepository.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRepository.kt)
- public API of [ForecastOverlayViewModel.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayViewModel.kt)
- Hilt bindings in [ForecastModule.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/di/ForecastModule.kt)
- map UI integration code
- weather/overlay manager code

## Verification

Run serially on this Windows machine:

```bat
./gradlew.bat :feature:profile:compileDebugKotlin :feature:map:compileDebugKotlin :app:assembleDebug --console=plain --no-daemon
```

Then:

```bat
./gradlew.bat :feature:profile:compileDebugUnitTestKotlin :feature:map:compileDebugUnitTestKotlin --console=plain --no-daemon
```

And finally:

```bat
./gradlew.bat enforceRules --console=plain --no-daemon
```

Important:

- do not run these in parallel
- if Windows file locks appear, use the existing lock-recovery procedure from
  [README.md](/C:/Users/Asus/AndroidStudioProjects/XCPro/docs/GRADLE/README.md)

## Benchmark

Use the retained historical edit-sensitive baseline only. No ad hoc benchmark
changes.

```powershell
./gradlew.bat :app:compileDebugKotlin --profile --console=plain
```

Success interpretation:

- best case: current profiled compile cost improves versus the retained
  historical `map-impl` baseline
- acceptable case: the profiled compile path is neutral, but forecast runtime
  ownership is now outside `feature:map`
- failure case: profiled compile cost regresses materially and compile churn
  increased

## Stop Conditions

Stop immediately if any of these happen:

- the moved runtime needs map overlay-manager imports
- `feature:profile` starts needing `feature:map`
- repository/ViewModel/Hilt churn expands beyond light compile-fix fallout
- tests require broad rewrite instead of import-only adjustment
- the diff expands into weather runtime or overlay rendering logic

## Rollback Condition

Rollback this slice if:

- you need to change forecast behavior to make it compile
- you need to move repository ownership as part of the same change
- you need to change DI structure beyond import-level fallout

## Expected Payoff

Expected payoff is medium.

What this slice should do:

- reduce `feature:map` ownership of forecast runtime code
- lower compile breadth for forecast-runtime edits
- establish the pattern for the next forecast/weather runtime split

What this slice probably will not do alone:

- solve `:feature:map:kspDebugKotlin`
- solve `:app:kspDebugKotlin`
- eliminate the entire first-run spike

## Next Step After This Slice

Only after this slice is measured and reviewed:

1. decide whether the next bounded target is
   [MapOverlayManagerRuntimeForecastWeatherDelegate.kt](/C:/Users/Asus/AndroidStudioProjects/XCPro/feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt)
   or to stop forecast extraction if the payoff is too small
2. do not combine that next step into the same change
