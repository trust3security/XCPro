# GRADLE Build Iteration

Purpose: this folder is the canonical handoff point for local build-speed and
edit-iteration work in this repo.

Use this read order before changing build-performance structure:

1. `docs/GRADLE/README.md`
2. `docs/GRADLE/BASELINE_BUILD_MEASUREMENTS_2026-03-10.md`
3. `docs/GRADLE/CHANGE_PLAN_BUILD_ITERATION_REDUCTION_2026-03-10.md`
4. `scripts/dev/README.md`

## Current state

- Gradle wrapper: `8.13`
- JVM: Android Studio JBR `21.0.8`
- Current Gradle tuning in `gradle.properties` includes:
  - `org.gradle.caching=true`
  - `org.gradle.configuration-cache=true`
  - `org.gradle.parallel=true`
  - `org.gradle.vfs.watch=true`
  - `kotlin.compiler.execution.strategy=in-process`
- `kotlin.incremental.useClasspathSnapshot=true`

## Lock handling

The local "speed-loop" scripts now run Gradle directly:

- `preflight.bat`
- `check-quick.bat`
- `auto-test.bat`
- `dev-fast.bat`
- `build-only.bat`
- `deploy.bat`
- `repair-build.bat`

No helper retry wrapper is used in these fast loops.

## Fast local loop for compile-only iteration

The local speed scripts now default to compile-only loops:

- `preflight.bat`: rules + assemble only
- `check-quick.bat`: rules + assemble only (tests only when passed as explicit args)
- `auto-test.bat`: rules + assemble only

To add tests, pass Gradle args explicitly:

```bat
check-quick.bat :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapScreenViewModelTest"
```

## What the measurements say

Warm no-edit local tasks are already reasonably fast.

- `:feature:map:compileDebugKotlin` median about `1.57s`
- `:feature:map:testDebugUnitTest` median about `1.69s`
- `:app:assembleDebug` median about `1.63s`

The real pain is edit-sensitive rebuild breadth.

- `app` implementation-only edit: about `4.43s`
- `core:common` implementation-only edit: about `9.50s`
- `feature:map` implementation-only edit: about `21.17s`
- shared ABI edits can spike much higher on the first rebuild

Conclusion: generic Gradle flags are no longer the highest-value work item.
The main problem is broad downstream invalidation from `feature:map` and shared
ABI churn from `core:common`.

## Fast Lock Recovery

If Windows reports locked files during `compile`/`assemble`/`test` runs:

```bat
.\scripts\dev\kill_stale_gradle_processes.ps1 -ProjectRoot .
powershell -NoProfile -Command "Get-ChildItem .\feature\map\build\test-results\testDebugUnitTest\binary, .\app\build\test-results\testDebugUnitTest\binary -Recurse -ErrorAction SilentlyContinue | Remove-Item -Force -Recurse -ErrorAction SilentlyContinue"
```

Run this, then rerun your build command.

## Benchmark entrypoints

- `scripts/dev/measure_map_build.ps1`
  - measures repeated no-edit task timing
- `scripts/dev/measure_edit_impact.ps1`
  - measures rebuild cost after controlled source edits in `app`,
    `feature:map`, and `core:common`

## First implementation step

Do not start by adding more cache flags.

Start by extracting one implementation-only traffic render/runtime slice out of
`feature:map` behind a narrow facade, while keeping current behavior and map
ownership intact.

Best first target:

- `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanelsAdsb.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanelsOgn.kt`
- related support/config classes already being split around the traffic runtime

Why this first:

- high-churn implementation code
- limited Hilt/KSP coupling compared with ViewModel/DI layers
- good chance of reducing `feature:map` rebuild breadth without changing SSOT
  ownership or replay behavior

See `CHANGE_PLAN_BUILD_ITERATION_REDUCTION_2026-03-10.md` for the phased plan.
