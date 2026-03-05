# MapScreen Baseline Profiling and SLO Matrix

Date: 2026-03-05  
Owner: XCPro Team  
Status: Draft

## 1) Purpose

Define a measurable visual UX contract for MapScreen movement and overlays,
plus engineering SLOs that make the UX contract enforceable.

## 2) Visual UX Contract (Mandatory)

| SLO ID | User-visible outcome | SLI definition | Target SLO | Failure trigger |
|---|---|---|---|---|
| MS-UX-01 | Smoother pan/zoom/rotate while moving | Frame time and jank during continuous pan/zoom/rotate with overlays enabled | frame time p95 <= 16.7 ms, p99 <= 24 ms, jank <= 5% | any threshold missed in required scenario |
| MS-UX-02 | Less stutter while dragging task points/overlays | Input event to first visible drag-preview update | latency p95 <= 50 ms, p99 <= 75 ms, full task teardown per move = 0 | latency threshold missed or any per-move full teardown |
| MS-UX-03 | Fewer marker jumps for traffic/weather/OGN | Snap-event rate where same-target screen delta > 24 px between adjacent rendered frames, update gap <= 2 s | <= 1 snap event per target per 5 min stress window | snap rate exceeds target |
| MS-UX-04 | Less flicker / z-order popping | Reorder operations in steady state and by transition | redundant reorders with unchanged state = 0 per minute; <= 1 reorder per real state transition | any steady-state redundant reorder or transition overrun |
| MS-UX-05 | Cleaner replay scrubbing and playback | Scrub release to stable rendered map+overlay state | latency p95 <= 120 ms, p99 <= 180 ms; no-op recompute on unchanged replay selection = 0 | latency miss or no-op recompute > 0 |
| MS-UX-06 | Faster first-map readiness with fewer redraw bursts | Entry to first stable map (camera + base style + enabled overlays settled) | cold start p95 <= 1800 ms, warm start p95 <= 900 ms, redraw bursts in first 2 s <= 2 | any threshold missed |

## 3) Supporting Engineering SLOs

| SLO ID | Metric | Target SLO | Notes |
|---|---|---|---|
| MS-ENG-01 | Overlay update apply duration p95 | <= 30 ms | per runtime apply batch |
| MS-ENG-02 | Startup overlay init (style-ready -> overlays-ready) p95 | <= 400 ms | startup stabilization |
| MS-ENG-03 | ADS-B per-frame feature build p95 | <= 8 ms | dense target scenario |
| MS-ENG-04 | OGN publish ordering path | no unconditional full sort on unchanged keys | deterministic ordering preserved |
| MS-ENG-05 | SCIA dense-trail render pass p95 | <= 20 ms at 12k rendered segments | dense stress condition |
| MS-ENG-06 | Lifecycle sync invocations per owner-state transition | <= 1 | no recomposition-driven sync loops |
| MS-ENG-07 | ADS-B smoothing retarget window stability | p95 <= 2000 ms and no zero-seeded sample windows in steady-state updates | guards smoother timestamp regressions |
| MS-ENG-08 | Weather rain cache identity correctness | tile-size/config mismatch rebuild miss count = 0 | no stale `RasterSource` reuse |
| MS-ENG-09 | Root recomposition pressure | `MapScreenRoot` recomposition count under dense traffic <= baseline * 0.60 | bind/fanout containment |
| MS-ENG-10 | Render cadence owner integrity | duplicate frame-owner count = 0 per 5 min replay/live stress run | prevents dual-pump pressure |
| MS-ENG-11 | OGN selection hot-path allocation | bytes/op and alloc count <= baseline * 0.50 in dense selection checks | removes per-candidate alias allocation churn |

## 4) Baseline Scenarios

1. Idle map with all overlays disabled.
2. Pan/zoom with OGN enabled.
3. Pan/zoom with OGN + ADS-B + rain animation enabled.
4. AAT edit drag session (continuous drag 30+ seconds).
5. Cold startup map style load with forecast/satellite/rain enabled.
6. Replay mode with render-frame sync active and overlays enabled.
7. Dense traffic + SCIA trail scenario (50 aircraft target load).
8. Mixed-load stress: pan + drag + weather animation + replay scrub transitions.

## 4A) Scenario Execution Protocol (Mandatory)

1. Run each scenario on two device tiers:
   - Tier A primary: current flagship/perf target.
   - Tier B secondary: mid-tier reference device.
2. Each scenario run duration:
   - standard scenarios: minimum 5 minutes.
   - startup scenarios: 20 cold + 20 warm launches.
3. Each SLO must be measured across `N >= 5` runs per device tier.
4. Reject a run if instrumentation dropped samples > 1% or traces are incomplete.
5. Use the same build variant and config set across baseline and post-change runs.

## 5) Instrumentation Points (Expected)

1. Task render sync coordinator:
   - sync trigger count/minute
   - full teardown count/minute
   - drag input-to-preview latency p95/p99
2. Overlay manager runtime:
   - front-order invocation count
   - per-overlay apply duration
   - steady-state redundant reorder count
3. Map initializer and map-ready callback:
   - overlay reapply invocation count by map generation
   - first stable map timing (cold/warm)
4. ADS-B overlay:
   - frame callback cost
   - feature-build duration
   - active-target count
   - marker snap-event counters
5. OGN runtime publish:
   - sort invocation count
   - publish latency
6. Replay observer/runtime path:
   - scrub release to stable-render latency
   - no-op recompute count due unchanged replay selection
7. SCIA trail pipeline:
   - filter duration, render duration
   - rendered segment count
8. ADS-B smoothing:
   - retarget duration distribution (p50/p95/p99)
   - branch counters for stationary/no-animation timestamp updates
9. Weather rain cache/runtime:
   - cache hit count by frame key
   - cache rebuild count by tile-size/config change
10. Compose root fanout:
   - recomposition counters for root and key binding groups
   - effect restart counters for list-keyed side effects
11. Render cadence owner:
   - frame owner source counters (compose loop vs render-frame sync)
   - duplicate-owner frame count
12. OGN key selection:
   - allocation bytes/op and allocations/op in dense key matching path

## 6) Data Capture Contract

- Capture in debug builds only.
- Use aggregated counters/timers; avoid noisy per-frame logs.
- Use monotonic timing for duration metrics.
- Keep telemetry optional and removable after stabilization.
- For each impacted SLO ID, attach baseline + post-change evidence.
- Capture evidence on one primary and one secondary device tier.
- Any mandatory SLO miss requires a time-boxed `KNOWN_DEVIATIONS.md` entry.
- Persist run artifacts under `artifacts/mapscreen/<phase>/<run-id>/`.
- Include run manifest (`manifest.json`), metric snapshot (`metrics.json`), and trace links (`trace_index.json`).

## 6A) Statistical Contract (Mandatory)

1. Percentiles:
   - compute p50/p95/p99 from merged per-run samples after excluding invalid runs.
2. Stability:
   - require coefficient of variation (`stdev/mean`) <= 0.20 for key latency metrics.
3. Confidence:
   - report 95% confidence interval on p95 estimates using bootstrap resampling (`>= 1000` resamples).
4. Pass/fail:
   - pass only if target threshold is satisfied on both device tiers and CI upper bound is within threshold.
5. Regression:
   - fail if post-change p95 regresses >= 10% against baseline for same scenario/device tier.

## 7) Baseline Report Template

For each scenario:

- Device model:
- Build variant:
- Scenario runtime:
- Runs included / rejected:
- Impacted SLO IDs:
- Median frame time:
- p95 frame time:
- p99 frame time:
- p95 95% CI:
- dropped/slow frame count:
- sample count:
- notable spikes and correlated runtime events:
- pass/fail per SLO ID:
- artifact path:

## 8) Phase Gate Mapping

| Phase | Required SLO Gate |
|---|---|
| Phase 0 | baseline captured for all `MS-UX-*` and `MS-ENG-*` SLO IDs |
| Phase 1 | `MS-UX-02`, `MS-UX-04`, `MS-UX-06`, `MS-ENG-06`, `MS-ENG-10` pass |
| Phase 2 | `MS-UX-03`, `MS-UX-05`, `MS-ENG-01/03/04/05/07/08/09/11` pass |
| Phase 3 | `MS-UX-01` passes under mixed-load stress with no regression in prior SLOs |
| Phase 4 | all mandatory SLOs pass, or approved deviation recorded with issue/owner/expiry |
