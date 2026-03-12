# Baseline Build Measurements 2026-03-10

## Purpose

Record the measured local build baseline before further build-iteration
refactors.

## Machine and tool state

- OS: Windows 11
- Gradle wrapper: `8.13`
- Kotlin (Gradle runtime): `2.0.21`
- JVM: Android Studio JBR `21.0.8`
- Repo root: `C:\Users\Asus\AndroidStudioProjects\XCPro`
- Defender exclusions: not verified from non-admin shell

## Commands used

Warm no-edit timings:

```powershell
powershell -NoProfile -Command "& { .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugKotlin' 6>&1 }"
powershell -NoProfile -Command "& { .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:testDebugUnitTest' 6>&1 }"
powershell -NoProfile -Command "& { .\scripts\dev\measure_map_build.ps1 -Tasks ':app:assembleDebug' 6>&1 }"
```

Warm no-build-cache timings:

```powershell
powershell -NoProfile -Command "& { .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:compileDebugKotlin' -NoBuildCache 6>&1 }"
powershell -NoProfile -Command "& { .\scripts\dev\measure_map_build.ps1 -Tasks ':feature:map:testDebugUnitTest' -NoBuildCache 6>&1 }"
powershell -NoProfile -Command "& { .\scripts\dev\measure_map_build.ps1 -Tasks ':app:assembleDebug' -NoBuildCache 6>&1 }"
```

Historical edit-sensitive timings:

Collected before the dedicated edit-impact benchmark helper was retired from the
repo. The original command lines are intentionally omitted from current
guidance.

## Warm no-edit baseline

| Task | Avg ms | Median ms | Min ms | Max ms |
|---|---:|---:|---:|---:|
| `:feature:map:compileDebugKotlin` | 1569.6 | 1572.1 | 1524.2 | 1612.5 |
| `:feature:map:testDebugUnitTest` | 1694.5 | 1689.0 | 1679.5 | 1715.0 |
| `:app:assembleDebug` | 1662.2 | 1629.5 | 1623.0 | 1734.0 |

## Warm no-build-cache baseline

| Task | Avg ms | Median ms | Min ms | Max ms |
|---|---:|---:|---:|---:|
| `:feature:map:compileDebugKotlin` | 1541.7 | 1531.6 | 1524.9 | 1568.5 |
| `:feature:map:testDebugUnitTest` | 1605.3 | 1615.0 | 1578.1 | 1622.9 |
| `:app:assembleDebug` | 1621.4 | 1634.6 | 1575.1 | 1654.4 |

Observation: build cache is not the dominant limiter for warm steady-state
local tasks.

## Historical edit-sensitive benchmark

Reference steady-state no-edit baseline for `:app:compileDebugKotlin`:

| Task | Avg ms | Median ms | Min ms | Max ms |
|---|---:|---:|---:|---:|
| `:app:compileDebugKotlin` | 1601.0 | 1580.7 | 1538.4 | 1684.1 |

Measured rebuild cost after controlled source edits:

| Scenario | Meaning | Avg ms | Median ms | Min ms | Max ms |
|---|---|---:|---:|---:|---:|
| `app-impl` | implementation-only edit in `app` | 4572.0 | 4430.5 | 4377.2 | 4908.2 |
| `map-impl` | implementation-only edit in `feature:map` | 21256.4 | 21170.4 | 17814.3 | 24784.5 |
| `map-abi` | ABI edit in `feature:map` | 45337.1 | 19718.0 | 17260.5 | 99032.8 |
| `core-impl` | implementation-only edit in `core:common` | 15950.6 | 9500.0 | 8710.4 | 29641.3 |
| `core-abi` | ABI edit in `core:common` | 74683.9 | 20978.7 | 19075.3 | 183997.7 |

## Interpretation

1. `feature:map` implementation edits are the primary local iteration hotspot.
2. Shared ABI edits in `feature:map` and `core:common` are dangerous and can
   trigger very expensive first rebuilds.
3. Warm no-edit task speed is already acceptable; architecture and module
   boundaries are now the main lever.
4. KSP/Hilt/generated-state churn on Windows amplifies some first-run spikes.
   `repair-build.bat` is a valid recovery tool when generated state becomes
   inconsistent.

## Immediate conclusion

The next optimization step should reduce edit invalidation breadth, not add
more generic Gradle flags.
