# Profiling Playbook

Purpose:
- Produce repeatable thermal/battery evidence for the hotspots listed in baseline docs.
- Compare scenarios with one variable changed at a time.

## Pre-Flight Test Setup

1. Device prep
- Charge to at least 80%.
- Disable battery saver.
- Keep ambient conditions stable (indoors, similar temperature).
- Keep screen brightness fixed for all runs.

2. App prep
- Use same build variant for comparison runs.
- Start from clean app state where possible.
- Keep map style constant across scenarios.

3. Session duration targets
- Quick triage: 8 to 10 minutes.
- Deeper run: 20 to 30 minutes.

## Test Matrix (Minimum)

Run A (baseline):
- Levo vario on
- Map visible
- All traffic/weather overlays off

Run B:
- Same as A
- Audio disabled

Run C:
- Same as A
- OGN overlay on, display mode `REAL_TIME`

Run D:
- Same as C
- OGN display mode `BATTERY`

Run E:
- Same as A
- ADS-B overlay on

Run F:
- Same as A
- Weather rain animation on (if used by pilot)

## Instrumentation

1. Android Studio Profiler
- CPU: sample mode during each run window.
- Energy: capture high-level power trends.
- Memory: watch churn spikes during overlays.

2. System dumpsys checkpoints
- Before run:
  - `adb shell dumpsys batterystats --reset`
- During/after run:
  - `adb shell dumpsys thermalservice`
  - `adb shell dumpsys gfxinfo <package_name>`
  - `adb shell dumpsys batterystats --charged`

3. Optional Perfetto capture (recommended for top issues)
- Capture thread scheduling + CPU freq + frame timeline + binder.
- Align markers with scenario changes (toggle overlay/audio).

## What To Measure

For each run, collect:
- Battery drop (%), elapsed time, average drain per hour estimate.
- Thermal status changes from `thermalservice`.
- Frame stability and jank from `gfxinfo`.
- CPU load concentration by thread/package from profiler.
- User-visible symptoms (UI lag, delayed gestures, stutter audio).

## Fast Isolation Heuristics

1. If disabling audio drops heat clearly:
- Audio synthesis path is a major contributor for that device profile.

2. If switching OGN from `REAL_TIME` to `BATTERY` materially helps:
- Overlay cadence is a major contributor.

3. If map hidden/background state cools quickly:
- Foreground map render path dominates.

4. If weather animation causes immediate jank:
- Raster transition path is likely over budget on that device GPU.

## Evidence Mapping Rules

When documenting findings:
- Always include scenario ID and exact toggle states.
- Map each finding to concrete code path(s) with file/line references.
- Mark confidence as High/Medium/Low.
- Separate measured facts from inferred causes.

## Exit Criteria For "Confirmed Root Contributor"

A contributor is considered confirmed when:
- It is isolated by A/B scenario change.
- Thermal and battery metrics move in expected direction consistently.
- Effect reproduces on at least two runs.

If criteria are not met, keep status as hypothesis and log uncertainty.
