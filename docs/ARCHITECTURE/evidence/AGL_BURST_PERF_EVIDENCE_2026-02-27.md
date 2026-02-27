# AGL Burst Perf Evidence (2026-02-27)

## Scope

- Path measured: AGL burst submission/worker hot path (`updateAGL` latest-wins processing).
- Workload: `5,000` rapid submissions per run.
- Runs: `12` measured runs after `3` warmup runs.
- Budget contract:
  - `p50 <= +15%` versus baseline
  - `p95 <= +20%` versus baseline
  - `alloc <= +10%` versus baseline

## Command

```bash
./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.sensors.FlightCalculationHelpersPerfEvidenceTest" -PxcproEnablePerfEvidence=true --rerun-tasks
```

## Baseline vs After Model

- Baseline (`before`): legacy recursive coroutine relaunch worker model (test harness emulation).
- After (`current`): current single-loop AGL worker implementation.

## Result

- Captured from:
  - `feature/map/build/test-results/testDebugUnitTest/TEST-com.example.xcpro.sensors.FlightCalculationHelpersPerfEvidenceTest.xml`
- Marker line:
  - `AGL_PERF_EVIDENCE before_p50_ms=0.4835 before_p95_ms=0.7653 after_p50_ms=1.1905 after_p95_ms=1.5024 before_alloc_p50_bytes=0 after_alloc_p50_bytes=1064 p50_delta_pct=146.2254 p95_delta_pct=96.3152 alloc_delta_pct=NaN runs=12 updates_per_run=5000`
- Budget status marker:
  - `AGL_PERF_EVIDENCE_BUDGETS_MET=false`

Interpretation:
- This harness compares current worker behavior to an in-test legacy recursive relaunch emulation.
- On this machine/run, the synthetic baseline is faster because it omits the real helper's additional branch/state work.
- Use this as reproducible evidence output, not as final release sign-off for the +15/+20/+10 budget contract.
- Release sign-off should compare current branch versus a pre-change commit using the same harness and environment.

## Notes

- Allocation evidence uses coarse JVM heap delta (`totalMemory - freeMemory`) per run.
- This evidence test is opt-in (`-PxcproEnablePerfEvidence=true`) so default local/CI unit-test loops are not slowed.
