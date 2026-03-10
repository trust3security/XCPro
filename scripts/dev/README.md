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


## 1B) gradle-run-with-lock-recovery.bat

Use this for all lightweight script-runner paths that need one automatic lock-recovery retry.

```bat
.\scripts\dev\gradle-run-with-lock-recovery.bat .\gradlew.bat <gradle-args>
```

If the command fails, the wrapper runs:

- `scripts\dev\recover-gradle-file-locks.bat`
- stops gradle workers and cleans stale lock artifacts
- retries the same command once

This wrapper is now used by:

- `preflight.bat`
- `check-quick.bat`
- `auto-test.bat`
- `build-only.bat`
- `deploy.bat`
- `dev-fast.bat` (task execution paths)
- `test-safe.bat` (plus additional test output lock cleanup)
- `repair-build.bat` (plus KSP and wrapper-lock cleanup)

### Parallelism control

Set `XC_DISABLE_GRADLE_PARALLEL=1` (or `true`) to force serial execution for any
command run through this wrapper:

```bat
set XC_DISABLE_GRADLE_PARALLEL=1
```

This appends `--no-parallel` at runtime and can reduce memory pressure for
low-RAM Windows machines when compilation becomes unstable.

Clear it to restore normal wrapper behavior:

```bat
set XC_DISABLE_GRADLE_PARALLEL=
```

## 2) recover-gradle-file-locks.bat

Use this when a build/test run fails with lock-related errors.

```bat
.\scripts\dev\recover-gradle-file-locks.bat
.\scripts\dev\recover-gradle-file-locks.bat --aggressive
```

This performs:

- `gradlew --stop`
- stale Gradle Java process termination (repo-owned)
- removal of known lock artifacts and `.lck` files in build-related folders

## 3) measure_edit_impact.ps1

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
