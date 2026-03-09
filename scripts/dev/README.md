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
