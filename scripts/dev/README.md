# scripts/dev

Build-time utility notes for local performance measurement.

## 1) measure_map_build.ps1

Run repeated compile timing for a Gradle task from repo root.

```powershell
powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugUnitTestKotlin'
```

Common options:

- `-WarmupIterations <int>` (default `1`)  
  Throwaway runs to warm daemon/configuration/classpaths.
- `-MeasuredIterations <int>` (default `3`)  
  Timed runs used in stats.
- `-NoBuildCache`  
  Disable Gradle build cache for a cold/no-cache view.
- `-NoDaemon`  
  Force no-gradle-daemon mode.
- `-CleanFirst`  
  Run `clean` before timing.

Examples:

```powershell
# Baseline (default): build-cache on, daemon on, 1 warmup + 3 measured
powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugUnitTestKotlin'

# Compare cache/no-cache behavior
powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugUnitTestKotlin' -NoBuildCache
powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugUnitTestKotlin' -NoBuildCache -WarmupIterations 0 -MeasuredIterations 5

# Compare daemon/no-daemon mode (no cache recommended for this view)
powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugUnitTestKotlin' -NoDaemon -NoBuildCache -WarmupIterations 1 -MeasuredIterations 3
```

Baseline command set (recommended baseline protocol):

| Step | Command | Purpose |
|---|---|---|
| A | `powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugUnitTestKotlin'` | Repeated cached builds (expected fastest steady-state). |
| B | `powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugUnitTestKotlin' -NoBuildCache -WarmupIterations 1 -MeasuredIterations 3` | Removes build-cache effects (true task work floor). |
| C | `powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugUnitTestKotlin' -NoDaemon -NoBuildCache -WarmupIterations 1 -MeasuredIterations 2` | Removes daemon reuse variance (process startup + orchestration variance). |
| D | `powershell -NoProfile -File .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugUnitTestKotlin' -CleanFirst -NoBuildCache -WarmupIterations 0 -MeasuredIterations 1` | Hard floor check: clean + cold-cache single sample for trend alarms. |


## 1B) Manual lock cleanup (optional)

Use only if a local run blocks on stale test lock artifacts.

```bat
.\scripts\dev\kill_stale_gradle_processes.ps1 -ProjectRoot .
powershell -NoProfile -Command "Get-ChildItem .\feature\map\build\test-results\testDebugUnitTest\binary, .\app\build\test-results\testDebugUnitTest\binary -Recurse -ErrorAction SilentlyContinue | Remove-Item -Force -Recurse -ErrorAction SilentlyContinue"
```

## Fast test stripping

For true compile-only speed loops, use the default script behavior and add tests
only when explicitly requested:

- `preflight.bat`: now runs `enforceRules` + `assembleDebug` only.
- `check-quick.bat`: runs `enforceArchitectureFast` + targeted assemblies by default; pass extra Gradle args to run any tests explicitly.
- `auto-test.bat`: runs `enforceArchitectureFast` + `assembleDebug` only.

If you need tests, pass them directly:

```bat
check-quick.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.MapScreenViewModelTest"
```

## QA bundles

For repo-owned slice verification bundles, use:

```bat
scripts\qa\run_change_verification.bat -Profile fast-loop
scripts\qa\run_change_verification.bat -Profile slice-terrain
scripts\qa\run_change_verification.bat -Profile pr-ready
```

These bundles are intended to work with the `xcpro-build` skill and keep the
default local workflow lighter than immediately jumping to repo-wide
`testDebugUnitTest`.

For the canonical root unit-test gate with Windows lock recovery, use:

```bat
scripts\qa\run_root_unit_tests_reliable.bat
```

## Retired

The dedicated edit-impact benchmark helper and its synthetic benchmark sources
were removed from the repo.

Historical edit-sensitive measurements are retained in:

- `docs/GRADLE/BASELINE_BUILD_MEASUREMENTS_2026-03-10.md`
