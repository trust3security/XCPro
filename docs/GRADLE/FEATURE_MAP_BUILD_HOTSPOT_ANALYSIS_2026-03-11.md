# Feature Map Build Hotspot Analysis 2026-03-11

## Purpose

Document the measured evidence for the current local build-speed bottleneck.
This is analysis only. No production behavior changes are proposed here.

## Executive Summary

The dominant local iteration problem is still `feature:map`, but the root cause
is narrower than "the app recompiles too much."

For an implementation-only edit inside `feature:map`:

- the cost is dominated by `:feature:map:compileDebugKotlin`
- `:feature:map:kspDebugKotlin` is a meaningful secondary cost
- `:app:compileDebugKotlin` stayed `UP-TO-DATE`
- `:app:kspDebugKotlin` still reran and added a smaller downstream penalty

This means the main problem is the size and responsibility breadth of the
`feature:map` module itself, not generic Gradle cache tuning and not ordinary
downstream Kotlin recompilation in `app`.

## Key Evidence

### Existing baseline

From [BASELINE_BUILD_MEASUREMENTS_2026-03-10.md](./BASELINE_BUILD_MEASUREMENTS_2026-03-10.md):

- warm no-edit `:app:assembleDebug` median: `1629.5 ms`
- edit-sensitive `map-impl` scenario median: `21170.4 ms`
- edit-sensitive `map-abi` scenario median: `19718.0 ms`
- conclusion already recorded there: warm no-edit builds are acceptable; edit
  breadth is the real problem

### Controlled task profile after a `feature:map` implementation edit

Profile artifact:

- `build/reports/profile/profile-2026-03-11-13-28-03.html`

Observed task costs for `:app:compileDebugKotlin` after a synthetic
implementation-only benchmark edit in `feature:map` captured before the
dedicated edit-impact helper was retired:

| Task | Duration | Result |
|---|---:|---|
| `:feature:map:compileDebugKotlin` | `50.477s` | executed |
| `:feature:map:kspDebugKotlin` | `8.853s` | executed |
| `:feature:map:transformDebugClassesWithAsm` | `2.953s` | executed |
| `:feature:map:bundleLibCompileToJarDebug` | `0.510s` | executed |
| `:app:kspDebugKotlin` | `4.776s` | executed |
| `:app:compileDebugKotlin` | `0.028s` | `UP-TO-DATE` |

Interpretation:

- the measured pain is primarily inside `feature:map`
- downstream Kotlin recompilation in `app` is not the main issue for
  implementation-only edits
- downstream Hilt/KSP aggregation in `app` still adds cost

## Structural Evidence

`feature:map` is much broader than a UI shell.

Measured from local source tree on 2026-03-11:

- source files under `feature/map/src/main/java`: `776`
- total lines across those files: about `94,464`
- files containing `@Composable`: `184`
- files containing `@HiltViewModel`: `29`
- files containing `@Module`: `13`

Top directory buckets under `feature/map/src/main/java/com/trust3/xcpro`:

| Bucket | File count |
|---|---:|
| `map` | `226` |
| `tasks` | `224` |
| `screens` | `101` |
| `sensors` | `47` |
| `weather` | `32` |
| `forecast` | `21` |
| `di` | `16` |

Large-file examples:

- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt`: `494` lines
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`: `487` lines
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt`: `483` lines
- `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRuntime.kt`: `475` lines

The module mixes:

- map UI/runtime
- task logic and task UI
- forecast/weather
- sensors and flight data
- profile-scoped repositories
- Hilt modules and generated wiring

That breadth is sufficient on its own to produce expensive implementation-only
compiles.

## App Coupling Evidence

`app` depends directly on `feature:map`:

- `app/build.gradle.kts` includes `implementation(project(":feature:map"))`

`app` also imports map-owned types outside the screen entrypoint:

- direct imports from `app/src/main/java` into `com.trust3.xcpro.map.*`: `22`
- notable imports include:
  - `MapStyleRepository`
  - `QnhPreferencesRepository`
  - `MapTrailPreferences`
  - `MapWidgetLayoutRepository`
  - `MapScreenViewModel`
  - `MapScreen`

Important implication:

- `feature:map` is not only a UI feature module
- it is also a host for profile/settings repositories used by app-level code
- this increases downstream classpath and KSP coupling

## What Is Probably Not The Main Problem

- generic Gradle cache flags
  - current baseline already shows warm no-edit builds are fine
- `:app:compileDebugKotlin` for implementation-only `feature:map` edits
  - profiled sample stayed `UP-TO-DATE`
- resource processing
  - resource tasks in the profiled sample were effectively noise compared with
    Kotlin + KSP time

## Ranked Root Causes

### 1. Oversized mixed-responsibility `feature:map` module

Why it matters:

- almost every map-adjacent concern lives in one Kotlin source set
- a local implementation edit still forces a very large Kotlin compilation unit

Expected gain if reduced:

- high

### 2. KSP/Hilt work inside `feature:map`

Why it matters:

- the module applies `ksp` and Hilt plugins directly
- implementation edits still triggered `:feature:map:kspDebugKotlin` in the
  profiled sample

Expected gain if reduced:

- medium to high

### 3. Downstream `app` KSP aggregation

Why it matters:

- even when `:app:compileDebugKotlin` stays `UP-TO-DATE`, `:app:kspDebugKotlin`
  still reran for the `map-impl` sample

Expected gain if reduced:

- medium

### 4. ASM/jar transform overhead after `feature:map` changes

Why it matters:

- `transformDebugClassesWithAsm` and `bundleLibCompileToJarDebug` are not the
  main bottleneck, but they are non-trivial after each module recompile

Expected gain if reduced:

- low to medium

## Recommended Parallel Workstreams

These are ordered by expected local build-speed value and can be worked in
parallel if ownership is kept clean.

### Workstream A: Extract high-churn traffic/render implementation out of `feature:map`

Target:

- map traffic overlay runtime/delegate/debug implementation that is currently
  edited frequently but does not need to live in the same compile unit as the
  whole map feature

Why first:

- aligns with existing plan in
  [CHANGE_PLAN_BUILD_ITERATION_REDUCTION_2026-03-10.md](./CHANGE_PLAN_BUILD_ITERATION_REDUCTION_2026-03-10.md)
- likely highest-value implementation-only invalidation reduction
- can preserve current `MapOverlayManagerRuntime` facade
- execution brief:
  [FEATURE_MAP_TRAFFIC_EXTRACTION_EXECUTION_BRIEF_2026-03-11.md](./FEATURE_MAP_TRAFFIC_EXTRACTION_EXECUTION_BRIEF_2026-03-11.md)

### Workstream B: Move app-consumed profile/settings repositories out of `feature:map`

Initial candidates:

- `MapStyleRepository`
- `QnhPreferencesRepository`
- `MapTrailPreferences`
- `MapWidgetLayoutRepository`

Why:

- these are consumed directly by app profile snapshot/restore/cleanup code
- they do not need to remain bundled with the full map runtime
- reducing this coupling should shrink downstream `app` dependency/KSP exposure

### Workstream C: Carve a non-KSP runtime/UI support slice

Target:

- pure Kotlin/Compose runtime helpers that do not require Hilt modules, Room,
  or Android entrypoint generation

Why:

- the profiled sample shows KSP is a material part of edit cost
- moving frequently edited implementation-only code into a lighter submodule can
  avoid rerunning Hilt aggregation for that slice

### Workstream D: Tighten ABI boundaries after split

Target:

- facade types, public constants, public data classes, benchmark surfaces

Why:

- implementation-only edits are the first problem
- ABI churn remains the second problem and should be addressed after the first
  split stabilizes

## Explicit Non-Recommendations

Do not start with:

- adding more cache flags
- disabling correctness checks
- folding more code into `app`
- mixing UI/runtime extraction with unrelated behavior changes

Those paths either do not attack the measured bottleneck or make regression
risk worse.

## Suggested Handoff Commands

Baseline/reference:

```powershell
powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':app:assembleDebug'
./gradlew.bat :app:compileDebugKotlin --profile --console=plain
```

Use the retained edit-sensitive table in
`BASELINE_BUILD_MEASUREMENTS_2026-03-10.md` as the historical comparison point.

Task-level investigation:

```powershell
./gradlew.bat :app:compileDebugKotlin --profile --console=plain
```

Required verification after any structural split:

```powershell
./gradlew.bat enforceRules
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

## Bottom Line

The current evidence says:

- no-edit Gradle speed is not the blocker
- `feature:map` implementation recompilation is the blocker
- `app` Kotlin recompilation is not the main cost for implementation-only map edits
- `app` KSP still participates and should be reduced after the first split

The best next move is not more Gradle tuning. It is reducing the amount of code
that must rebuild when a small `feature:map` implementation detail changes.
