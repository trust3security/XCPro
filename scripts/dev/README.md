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
- `check-quick.bat`: runs rules + assemblies by default; pass extra Gradle args to run any tests explicitly.
- `auto-test.bat`: runs rules + `assembleDebug` only.

If you need tests, pass them directly:

```bat
check-quick.bat :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapScreenViewModelTest"
```

## 2) measure_edit_impact.ps1

Run edit-sensitive compile measurements against isolated benchmark files in:

- `core:common`
- `feature:map`
- `app`

Default command:

```powershell
powershell -NoProfile -File .\scripts\dev\measure_edit_impact.ps1 -Tasks ':app:compileDebugKotlin'
```

Useful options:

- `-ScenarioNames 'app-impl','map-impl','map-abi','core-impl','core-abi'`
- `-RepairOnFailure` to clear stale KSP/generated state and retry once
- `-NoBuildCache`
- `-NoDaemon`

Example:

```powershell
powershell -NoProfile -File .\scripts\dev\measure_edit_impact.ps1 -Tasks ':app:compileDebugKotlin' -ScenarioNames 'map-impl','core-abi' -RepairOnFailure
```
