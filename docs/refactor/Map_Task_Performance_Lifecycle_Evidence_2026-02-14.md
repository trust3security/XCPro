# Map Task Performance and Lifecycle Evidence (2026-02-14)

## Scope

This document records the Workstream G evidence pack for the map/task slice:

- Task render sync dispatch latency budget check.
- Lifecycle stress scenario for background/foreground transitions.
- Reproducible commands and observed results.

## Environment

- Date: 2026-02-14
- Host: Windows (PowerShell)
- Device: `SM-S908E` (adb id: `R5CT2084XHN`)
- App package: `com.example.openxcpro.debug`
- Activity: `com.example.xcpro.MainActivity`

## 1) Task Render Dispatch Latency Check

Guard test:

- `feature/map/src/test/java/com/example/xcpro/map/TaskRenderSyncCoordinatorPerformanceTest.kt`

Budget:

- `TaskRenderSyncCoordinator.onTaskMutation()` dispatch call max <= `100 ms`.

Verification command:

```bash
./gradlew :feature:map:testDebugUnitTest --tests "*TaskRenderSyncCoordinatorPerformanceTest*"
```

Result:

- PASS (2026-02-14)

Notes:

- Test includes warm-up iterations and checks max single-call latency against the 100 ms guardrail.
- This is a micro-latency guard for coordinator dispatch path (not full map rendering GPU time).

## 2) Lifecycle Stress Scenario

Automation script:

- `scripts/qa/map_task_lifecycle_stress.ps1`

Verification command used:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/map_task_lifecycle_stress.ps1 -Iterations 6
```

Scenario:

1. Start app activity.
2. Send HOME (background app).
3. Resume app activity with `am start -W`.
4. Repeat for N cycles.
5. Collect launch timing (observed = `TotalTime` when available, otherwise `WaitTime`).

Observed results (ms):

- Initial start: observed `435` (total `435`, wait `463`)
- Cycle 1: observed `491`
- Cycle 2: observed `484`
- Cycle 3: observed `254`
- Cycle 4: observed `378`
- Cycle 5: observed `262`
- Cycle 6: observed `360`
- Summary:
  - min: `254`
  - avg: `371.5`
  - max: `491`

Notes:

- No crash/hang observed during automated background/foreground loops.
- Timing is app/activity launch timing and should be tracked longitudinally as a regression indicator.

## 3) Manual Lifecycle/Feature Stress Checklist

Manual scenario to run on release candidates:

1. Start replay, then pause/resume replay 5x.
2. Toggle task overlay and enter/exit AAT edit mode 5x.
3. Switch map style while task overlay is visible.
4. Background app for 5-10 seconds, then foreground.
5. Confirm no stuck overlays, no duplicated task layers, and no replay/task state loss.

## 4) Conclusion

Workstream G evidence is available and reproducible:

- Latency guard test exists and is passing.
- Lifecycle stress script exists and was executed with recorded metrics.
- Manual lifecycle checklist is documented for release validation.
