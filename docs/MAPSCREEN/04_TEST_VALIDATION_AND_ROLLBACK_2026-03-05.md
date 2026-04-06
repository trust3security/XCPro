# MapScreen Test, Validation, and Rollback Plan

Date: 2026-03-05  
Owner: XCPro Team  
Status: Draft

## 1) Validation Strategy

Use a layered validation model:

1. Unit tests for policy and deterministic behavior.
2. Integration tests for runtime wiring and lifecycle edges.
3. UI/instrumentation tests for map interactions and overlay visibility.
4. Performance evidence runs on representative devices.
5. SLO-gated phase exits tied to measurable visual UX outcomes.

## 2) Required Verification Commands

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant and available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

Connected device preflight (physical devices):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/prepare_connected_device_for_tests.ps1
```

Windows wrapper:

```bat
scripts\qa\run_connected_device_preflight.bat
```

Notes:

- Run preflight immediately before connected instrumentation.
- This avoids false failures caused by device doze/lock state (example symptom: `No compose hierarchies found in the app`).
- If needed after testing, disable stay-awake:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/prepare_connected_device_for_tests.ps1 -DisableStayAwake
```

## 2A) Evidence Artifact Contract (Mandatory)

For each phase/package, attach:
1. `manifest.json` with commit SHA, device tier, scenario ID, run count.
2. `metrics.json` with raw and aggregated SLI/SLO values (p50/p95/p99, CI).
3. `trace_index.json` with links/paths to perfetto, macrobenchmark, and profiler captures.
4. `gate_result.json` with pass/fail by SLO ID and rollback recommendation.
5. `arch_gate_result.txt` with `scripts/arch_gate.py` status.
6. `timebase_citations.md` citing:
   - `core/time/src/main/java/com/example/xcpro/core/time/Clock.kt`
   - `app/src/main/java/com/example/xcpro/di/TimeModule.kt`

Required path convention:
`artifacts/mapscreen/<phase>/<package-id>/<timestamp>/`.

## 2B) One-Command Package Capture (Windows)

Use these commands to scaffold Tier A/B trace folders and generate
checkpoint evidence files for Phase 1/2/3 packages:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_pkg_f1_evidence_capture.ps1
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_pkg_d1_evidence_capture.ps1
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_pkg_g1_evidence_capture.ps1
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_pkg_w1_evidence_capture.ps1
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_pkg_e1_evidence_capture.ps1
```

Optional flags:

- `-RunId <yyyyMMdd-HHmmss>` to force a deterministic artifact directory name.
- `-DryRun` to generate scaffold files without executing required gates.
- `-SkipRequiredGates` to scaffold only.
- `-SkipPostCaptureThresholdCheck` to opt out of the default sequential metric-template seed + threshold verifier pass.
- `-RunConnectedAppTests` and/or `-RunConnectedAllModulesAtEnd` when a device is attached.
- `-RequireConnectedDevice` to fail fast if no device/emulator is detected.

By default, each package wrapper now runs the follow-up steps sequentially after scaffold generation:

1. `seed_mapscreen_metric_templates.ps1`
2. `verify_mapscreen_package_evidence.ps1 -UpdateGateResult -AllowPending`

This prevents seed/verify from racing ahead of scaffold creation and ensures `threshold_check.json`
is emitted for the new run before any manual perf metrics are attached.

Each command creates:

- `tier_a/traces/perfetto/`, `tier_a/traces/macrobenchmark/`, `tier_a/traces/profiler/`
- `tier_b/traces/perfetto/`, `tier_b/traces/macrobenchmark/`, `tier_b/traces/profiler/`
- per-tier `capture_checklist.md`
- top-level `RUNBOOK.md`, `manifest.json`, `metrics.json`, `trace_index.json`, `gate_result.json`,
  `arch_gate_result.txt`, and `timebase_citations.md`.

## 2C) Automated Threshold Verification (MAPSCREEN-007)

The package wrappers above already seed templates and run an initial threshold pass sequentially.
Use the threshold verifier manually after Tier A/B metrics are filled in `metrics.json`:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/verify_mapscreen_package_evidence.ps1 -PackageId pkg-e1 -UpdateGateResult
```

Optional pre-step to seed required metric keys into package `metrics.json`:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/seed_mapscreen_metric_templates.ps1
```

Run across all package lanes:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_evidence_threshold_checks.ps1 -UpdateGateResults
```

## 2D) Automated Completion Contract Runner

Use this runner to execute the end-to-end MAPSCREEN completion contract with
stateful resume support:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1
```

State file:

`logs/phase-runner/mapscreen-completion-contract-state.json`

Resume from failure:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1 -Resume -FromPhase <phaseId>
```

Run with connected test lanes and strict device requirement:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1 `
  -RunConnectedAppTestsForPkgE1 `
  -RunConnectedAppTestsAtEnd `
  -RunConnectedAllModulesAtEnd `
  -RequireConnectedDevice
```

Allow continuation for reporting/finalization when threshold failures are known:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1 `
  -AllowPkgE1ThresholdFailure `
  -AllowThresholdRollupFailure `
  -AllowNonGreenReleasePackage
```

Current required metric-key contract in `metrics.json -> slo_targets[*].post_change`:

- `MS-UX-01`: `tier_a/tier_b.frame_time_p95_ms`, `frame_time_p99_ms`, `jank_percent`
- `MS-UX-05`: `tier_a/tier_b.scrub_latency_p95_ms`, `scrub_latency_p99_ms`, `noop_recompute_count`
- `MS-ENG-03`: `tier_a/tier_b.adsb_feature_build_p95_ms`
- `MS-ENG-04`: `tier_a/tier_b.unconditional_full_sort_on_unchanged_keys` (or count form)
- `MS-ENG-05`: `tier_a/tier_b.scia_dense_trail_render_p95_ms`
- `MS-ENG-07`: `tier_a/tier_b.adsb_retarget_window_p95_ms`, `zero_seeded_window_count`
- `MS-ENG-08`: `tier_a/tier_b.tile_size_mismatch_rebuild_miss_count`
- `MS-ENG-11`: `tier_a/tier_b.ogn_selection_alloc_bytes_ratio_vs_baseline`, `ogn_selection_alloc_count_ratio_vs_baseline`

Verifier outputs:

- `threshold_check.json` in the artifact directory
- updated `gate_result.json` when `-UpdateGateResult` is set

## 3) Test Matrix

| Area | SLO IDs | Test Type | Minimum Coverage |
|---|---|---|---|
| AAT drag render path | `MS-UX-02` | unit + integration + perf | drag move does not trigger full teardown per move; latency p95/p99 within threshold |
| Weather/traffic ordering | `MS-UX-04` | unit + integration | no repeated reorder on animation ticks; transition-only ordering updates |
| Startup overlay init | `MS-UX-06`, `MS-ENG-02` | integration + perf | one startup apply per map generation; startup/readiness thresholds met |
| ADS-B frame loop | `MS-ENG-03` | unit/perf | no duplicated frame-list traversal in single frame; p95 cost within threshold |
| OGN publish ordering | `MS-ENG-04` | unit | no unconditional full sort on unchanged ordering keys |
| Replay scrubbing/observer | `MS-UX-05` | integration + perf | scrub latency p95/p99 within threshold; no-op recompute on unchanged selection |
| Marker stability (OGN/ADS-B/weather) | `MS-UX-03` | integration + perf | snap-event rate within threshold under dense traffic scenario |
| SCIA trail path | `MS-ENG-05` | perf + regression | dense-trail render p95 within threshold with parity in output |
| Lifecycle fanout path | `MS-ENG-06` | integration | lifecycle sync <= 1 per owner-state transition |
| ADS-B smoother timestamp integrity | `MS-ENG-07` | unit + perf | no zero-seeded/stale sample-time interpolation windows |
| Weather rain cache identity | `MS-ENG-08` | integration + perf | tile-size/config changes trigger correct cache rebuilds |
| Root recomposition/fanout pressure | `MS-ENG-09` | compose runtime + perf | root recomposition count reduced to target bound |
| Render cadence owner integrity | `MS-ENG-10` | integration + perf | duplicate frame-owner count = 0 |
| OGN selection allocation churn | `MS-ENG-11` | unit + allocation perf | allocation and bytes/op reduced to target bound |
| Replay determinism | cross-cutting | unit/integration | no behavior drift for same replay inputs |

## 4) SLO-to-Test Mapping (Mandatory)

| SLO ID | Primary Harness | Pass Rule | Rollback Trigger |
|---|---|---|---|
| `MS-UX-01` | map motion macrobenchmark + jank stats run | p95/p99/jank all pass | any threshold miss in mixed-load run |
| `MS-UX-02` | drag gesture instrumentation + runtime counters | latency p95/p99 pass and teardown-per-move = 0 | teardown count > 0 or latency miss |
| `MS-UX-03` | dense-traffic playback/stress scenario | snap-event rate <= threshold | snap rate exceeds threshold |
| `MS-UX-04` | weather animation + traffic ordering test | redundant reorder count = 0 | any steady-state redundant reorder |
| `MS-UX-05` | replay scrub scenario + observer metrics | scrub latency pass; no-op recompute = 0 | latency miss or recompute > 0 |
| `MS-UX-06` | cold/warm startup profile run | cold/warm timing and redraw-burst thresholds pass | any startup threshold miss |
| `MS-ENG-01` | overlay apply micro-perf test | p95 <= threshold | p95 miss |
| `MS-ENG-02` | startup overlay integration test | style-ready -> overlays-ready p95 <= threshold | p95 miss |
| `MS-ENG-03` | ADS-B frame micro-perf | p95 <= threshold | p95 miss |
| `MS-ENG-04` | OGN ordering determinism test | no unconditional full sort on unchanged keys | sort policy regression |
| `MS-ENG-05` | SCIA dense-trail perf harness | p95 <= threshold with visual parity | p95 miss or parity failure |
| `MS-ENG-06` | lifecycle idempotency integration test | sync invocations <= threshold | invocation count regression |
| `MS-ENG-07` | ADS-B smoother transition harness | p95 <= threshold and zero-seeded window count = 0 | threshold miss or zero-seeded count > 0 |
| `MS-ENG-08` | weather rain cache identity test | mismatch count = 0 for tile-size/config transitions | mismatch count > 0 |
| `MS-ENG-09` | compose recomposition harness | root recompositions <= threshold bound | recomposition overrun |
| `MS-ENG-10` | frame-owner cadence harness | duplicate owner count = 0 | duplicate owner count > 0 |
| `MS-ENG-11` | OGN selection allocation benchmark | alloc bytes/op and alloc/op <= threshold | allocation threshold miss |

## 5) Regression Triggers

Any of the following blocks phase completion and release promotion:

1. Any mandatory `MS-UX-*` SLO threshold miss.
2. Any mandatory `MS-ENG-*` SLO threshold miss.
3. p95 map pan frame time regresses by >= 10% against prior phase baseline.
4. Reorder/teardown counters increase after the relevant optimization phase.
5. Replay determinism tests fail.
6. Architecture rule gate failures appear.
7. Any mandatory artifact (`manifest.json`, `metrics.json`, `trace_index.json`, `gate_result.json`) missing.
8. Statistical pass rule violation (CI upper bound crosses SLO threshold).

## 6) Rollback Plan

Rollback strategy is phase-scoped and reversible:

1. Isolate each workstream in separate commits/PRs.
2. Revert only the offending workstream if regression appears.
3. Keep instrumentation and safety tests in place during rollback.
4. Re-run required verification commands after rollback.
5. Preserve failing artifacts in `artifacts/mapscreen/rollback/<issue-id>/` for auditability.

## 6A) Statistical Pass Rules (Mandatory)

1. A metric passes only if:
   - threshold passes on both device tiers, and
   - 95% CI upper bound is within threshold.
2. A phase passes only if all impacted mandatory SLO IDs pass.
3. If run-to-run coefficient of variation > 0.20, mark result unstable and rerun before gate decision.
4. If baseline and post-change configuration differ, invalidate comparison.

## 7) Production Readiness Checklist

- [ ] Required gradle checks pass.
- [ ] Hotspot tests added and stable.
- [ ] Baseline vs post-change metrics attached.
- [ ] Mandatory artifact set attached for each impacted package.
- [ ] Impacted `MS-UX-*` and `MS-ENG-*` SLO IDs have explicit pass evidence.
- [ ] Replay determinism unchanged.
- [ ] No unapproved deviations introduced.
- [ ] Pipeline doc updated if flow wiring changed.
- [ ] Quality rescore completed with evidence.

## 8) Quality Rescore Template

- Architecture cleanliness: __ / 5
- Maintainability/change safety: __ / 5
- Test confidence on risky paths: __ / 5
- Overall MapScreen slice quality: __ / 5
- Release readiness: __ / 5

Evidence required:

- modified files,
- tests added/updated,
- verification command results,
- residual risks and owners.

## 9) Quality Rescore Formula (/100)

Use weighted scoring for section-level quality reporting:
1. Architecture contract quality: `25%`.
2. Data-flow precision quality: `25%`.
3. Phase-plan execution quality: `25%`.
4. Scorecard evidence quality: `25%`.

Scoring rules:
1. Missing mandatory artifact in any impacted package caps total at `94`.
2. Missing owner/dependency/entry-exit gate on any package caps phase-plan quality at `92`.
3. Missing statistical CI report caps scorecard quality at `90`.
4. Replay determinism gap caps overall at `90`.
