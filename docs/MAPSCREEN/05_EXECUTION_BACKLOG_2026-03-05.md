# MapScreen Execution Backlog

Date: 2026-03-05  
Owner: XCPro Team  
Status: Active (phase-2 lanes green; phase-3 `pkg-e1` blocked on `MS-UX-01` per `RULES-20260305-12`)

## 1) Backlog Prioritization Rules

Priority is based on:

1. Runtime impact on map movement and interaction.
2. Risk of user-visible jank/stutter.
3. Architectural safety and ease of rollback.

## 2) P0 Items (Immediate)

### MAPSCREEN-001: Decouple AAT drag preview from full task sync
- Workstream: A
- Priority: P0
- Outcome: drag move path avoids full clear/replot loop.
- Evidence gate: drag sync-count reduction and interaction latency improvement.

### MAPSCREEN-002: Idempotent centralized overlay ordering policy
- Workstream: B
- Priority: P0
- Outcome: weather animation does not cause repetitive traffic/ownship front-order churn.
- Evidence gate: reorder counter bounded by state-change events with steady-state redundant reorder = 0.

### MAPSCREEN-003: Remove duplicate startup overlay reapply path
- Workstream: C
- Priority: P0
- Outcome: one startup overlay apply per map generation.
- Evidence gate: startup apply counter equals 1 per generation.

### MAPSCREEN-008: Stop recomposition-driven lifecycle sync/display-frame work
- Workstream: F
- Priority: P0
- Outcome: `AndroidView.update` path no longer causes repeated lifecycle sync and
  opportunistic display-frame renders.
- Evidence gate: lifecycle sync count is stable/one-shot per owner-state transition.

## 3) P1 Items

### MAPSCREEN-004: ADS-B single-frame data reuse + smoother timestamp integrity
- Workstream: D
- Priority: P1
- Outcome: eliminate duplicate frame-list traversals in single visual frame and remove
  stale/zero-seeded sample-time interpolation windows.
- Evidence gate: ADS-B frame callback CPU reduction with `MS-ENG-03` and `MS-ENG-07` pass.

### MAPSCREEN-005: OGN publish ordering optimization
- Workstream: D
- Priority: P1
- Outcome: avoid unconditional full sort on unchanged keys.
- Evidence gate: reduced sort invocation/cost under dense stream.

### MAPSCREEN-009: Ownship altitude fanout cadence control for overlay updates
- Workstream: F
- Priority: P1
- Outcome: ownship altitude changes no longer trigger avoidable high-frequency
  OGN/ADS-B overlay refresh and layer-order churn.
- Evidence gate: lower overlay-update frequency with preserved label correctness.

### MAPSCREEN-010: Replace deep list equality in hot update paths with lightweight signatures
- Workstream: F
- Priority: P1
- Outcome: reduce O(n) list-compare overhead in frequent overlay update checks.
- Evidence gate: measured CPU reduction in dense traffic scenarios.

### MAPSCREEN-011: Replay observer dedupe and logging hardening
- Workstream: G
- Priority: P1
- Outcome: replay session progress updates no longer trigger avoidable map observer recompute;
  replay diagnostics logging is explicitly bounded/gated.
- Evidence gate: reduced observer invocation count under replay with unchanged behavior.

### MAPSCREEN-012: SCIA dense trail path optimization
- Workstream: G
- Priority: P1
- Outcome: reduce full-list filter/compare/rebuild pressure in SCIA trail pipeline while
  preserving deterministic visual output.
- Evidence gate: lower render/filter cost in 50-aircraft dense trail stress scenarios.

### MAPSCREEN-014: Weather rain cache identity hardening (tile-size/config-safe)
- Workstream: B/F
- Priority: P1
- Outcome: frame-cache reuse honors normalized tile-size/config identity to prevent stale
  `RasterSource` reuse.
- Evidence gate: `MS-ENG-08` pass with mismatch count = 0 across tile-size/config transitions.

## 4) P2 Items

### MAPSCREEN-006: Cadence/backpressure policy for mixed-load runtime
- Workstream: E
- Priority: P2
- Outcome: bounded update behavior under concurrent drag + weather animation + traffic updates.
- Evidence gate: p95/p99 stability in stress scenarios.

### MAPSCREEN-007: Extended performance harness and automated threshold checks
- Workstream: E
- Priority: P2
- Outcome: repeatable non-manual performance gate coverage.
- Evidence gate: automated reports integrated into development flow.

### MAPSCREEN-013: OGN selection check allocation cleanup
- Workstream: G
- Priority: P2
- Outcome: remove per-target transient allocation in selected-aircraft matching paths.
- Evidence gate: allocation profile improvement in dense OGN target updates.

## 5) Delivery Cadence Proposal

1. Sprint A:
   - MAPSCREEN-001, 002, 003, 008
2. Sprint B:
   - MAPSCREEN-004, 005, 009, 010, 011, 012, 014
3. Sprint C:
   - MAPSCREEN-006, 007, 013 and final hardening

## 6) Completion Criteria Per Item

Each backlog item is complete only when:

1. code change merged,
2. tests added and passing,
3. impacted `MS-UX-*` / `MS-ENG-*` SLO IDs have baseline vs post-change evidence,
4. rollback path documented,
5. no architecture-rule violations introduced,
6. artifact bundle is complete under `artifacts/mapscreen/<phase>/<package-id>/<timestamp>/`
   with `manifest.json`, `metrics.json`, `trace_index.json`, and `gate_result.json`,
7. verification contract includes:
   - `python scripts/arch_gate.py`
   - `./gradlew enforceRules`
   - `./gradlew testDebugUnitTest`
   - `./gradlew assembleDebug`
   - when relevant (device/emulator available):
     - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
     - `./gradlew connectedDebugAndroidTest --no-parallel`

Automation entrypoints (Phase 2/3 packages):
- `scripts/qa/run_mapscreen_pkg_d1_evidence_capture.ps1`
- `scripts/qa/run_mapscreen_pkg_g1_evidence_capture.ps1`
- `scripts/qa/run_mapscreen_pkg_w1_evidence_capture.ps1`
- `scripts/qa/run_mapscreen_pkg_e1_evidence_capture.ps1`
- `scripts/qa/seed_mapscreen_metric_templates.ps1` (seed required Tier A/B metric keys in `metrics.json`)
- `scripts/qa/run_mapscreen_evidence_threshold_checks.ps1` (automated SLO threshold gate evaluation)

## 7) Delivery Ledger (Mandatory)

| Item | Package ID | Owner Role | Dependencies | Target Phase | ETA Band | Required Artifact |
|---|---|---|---|---|---|---|
| MAPSCREEN-001 | PKG-A1 | task runtime owner | baseline drag metrics | Phase 1 | Sprint A | `artifacts/mapscreen/phase1/pkg-a1/<timestamp>/` |
| MAPSCREEN-002 | PKG-B1 | overlay runtime owner | MAPSCREEN-001 | Phase 1 | Sprint A | `artifacts/mapscreen/phase1/pkg-b1/<timestamp>/` |
| MAPSCREEN-003 | PKG-C1 | map init owner | MAPSCREEN-002 | Phase 1 | Sprint A | `artifacts/mapscreen/phase1/pkg-c1/<timestamp>/` |
| MAPSCREEN-008 | PKG-F1 | lifecycle/runtime owner | MAPSCREEN-003 | Phase 1 | Sprint A | `artifacts/mapscreen/phase1/pkg-f1/<timestamp>/` |
| MAPSCREEN-004 | PKG-D1 | ADS-B owner | MAPSCREEN-008 | Phase 2 | Sprint B | `artifacts/mapscreen/phase2/pkg-d1/<timestamp>/` |
| MAPSCREEN-005 | PKG-D1 | OGN owner | MAPSCREEN-008 | Phase 2 | Sprint B | `artifacts/mapscreen/phase2/pkg-d1/<timestamp>/` |
| MAPSCREEN-009 | PKG-E1 | overlay/runtime owner | MAPSCREEN-008 | Phase 3 | Sprint C | `artifacts/mapscreen/phase3/pkg-e1/<timestamp>/` |
| MAPSCREEN-010 | PKG-E1 | runtime optimization owner | MAPSCREEN-009 | Phase 3 | Sprint C | `artifacts/mapscreen/phase3/pkg-e1/<timestamp>/` |
| MAPSCREEN-011 | PKG-G1 | replay owner | MAPSCREEN-008 | Phase 2 | Sprint B | `artifacts/mapscreen/phase2/pkg-g1/<timestamp>/` |
| MAPSCREEN-012 | PKG-G1 | trail owner | MAPSCREEN-011 | Phase 2 | Sprint B | `artifacts/mapscreen/phase2/pkg-g1/<timestamp>/` |
| MAPSCREEN-014 | PKG-W1 | weather owner | MAPSCREEN-008 | Phase 2 | Sprint B | `artifacts/mapscreen/phase2/pkg-w1/<timestamp>/` |
| MAPSCREEN-006 | PKG-E1 | performance owner | PKG-D1, PKG-G1, PKG-W1 | Phase 3 | Sprint C | `artifacts/mapscreen/phase3/pkg-e1/<timestamp>/` |
| MAPSCREEN-007 | PKG-E1 | perf automation owner | MAPSCREEN-006 | Phase 3 | Sprint C | `artifacts/mapscreen/phase3/pkg-e1/<timestamp>/` |
| MAPSCREEN-013 | PKG-D1 | OGN owner | MAPSCREEN-012 | Phase 2-3 | Sprint C | `artifacts/mapscreen/phase2/pkg-d1/<timestamp>/` |

Execution-note:
- `pkg-e1` capture manifest currently enumerates `MAPSCREEN-006/007` as mandatory IDs.
- Include `MAPSCREEN-009/010` in next `pkg-e1` manifest refresh so backlog-to-artifact mapping is fully explicit.

## 8) Checkpoint Snapshot (2026-03-05)

1. MAPSCREEN-006/007: phase-3 package automation is now wired (`capture_mapscreen_phase2_package_evidence.ps1` supports `pkg-e1`; wrappers `run_mapscreen_pkg_e1_evidence_capture.ps1/.bat` added).
2. MAPSCREEN-009: ownship-altitude overlay fanout cadence control is now live at UI runtime boundary (`quantizeOverlayOwnshipAltitudeMeters`, 2 m default step), covered by `MapScreenRootEffectsTest`, and tracked in the phase-3 mixed-load package lane (`pkg-e1`).
3. MAPSCREEN-007: automated threshold checker is now implemented (`verify_mapscreen_package_evidence.ps1` + `run_mapscreen_evidence_threshold_checks.ps1/.bat`) and writes `threshold_check.json` per artifact.
4. MAPSCREEN-012: code path optimization implemented (`MapScreenUseCases`, `MapOverlayManagerRuntimeOgnDelegate`, `OgnGliderTrailOverlay`), test coverage added; package evidence capture pending.
5. MAPSCREEN-013: allocation cleanup implemented (`OgnAddressing`, `OgnTrailSelectionPreferencesRepository`, `MapScreenTrafficCoordinator`, `MapScreenViewModelStateBuilders`, `MapScreenContentRuntimeSupport`), test coverage added; allocation/perf artifact capture pending.
6. Required local verification for this checkpoint passed: `python scripts/arch_gate.py`, `./gradlew enforceRules`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`.
7. Threshold automation dry-run is verified: `scripts/qa/run_mapscreen_evidence_threshold_checks.ps1 -AllowPending` reports all current package lanes as `blocked_pending_perf_evidence` with per-package `threshold_check.json`.
8. Fresh package scaffolds generated: `artifacts/mapscreen/phase2/pkg-g1/20260305-151627/`, `artifacts/mapscreen/phase2/pkg-d1/20260305-151746/`, and `artifacts/mapscreen/phase3/pkg-e1/20260305-153337/` (all remain `blocked_pending_perf_evidence` until Tier A/B captures are attached).
9. Connected-device preflight automation added (`scripts/qa/prepare_connected_device_for_tests.ps1`, `scripts/qa/run_connected_device_preflight.bat`) to enforce awake/unlocked state before instrumentation and prevent doze-state false failures.
10. Full multi-module connected instrumentation gate re-run completed successfully after device preflight: `./gradlew connectedDebugAndroidTest --no-parallel --no-configuration-cache`.
11. Fresh phase-3 package run captured with connected-device metrics: `artifacts/mapscreen/phase3/pkg-e1/20260305-165030/` now includes `tier_a` and `tier_b` `gfxinfo_summary.json` captures (90 s each) and applied `MS-UX-01` metrics (`tier_a: p95=23 ms, p99=32 ms, jank=25.26%`; `tier_b: p95=27 ms, p99=34 ms, jank=30.31%`).
12. Threshold gate status for `pkg-e1` is now concrete `blocked_failed_thresholds` (no longer pending): `MS-UX-01` fails current threshold targets after both tier slots are populated.
13. Evidence tooling hardening applied: `scripts/qa/apply_pkg_e1_tier_metrics.ps1` now handles null/missing `post_change` tier properties under `Set-StrictMode` and updates SLO status to `captured`/`captured_partial` based on populated tier metrics.
14. Phase-2 package capture contract was tightened to match the backlog execution ledger: `pkg-d1` now includes `MAPSCREEN-004/005/013` with `MS-ENG-03/04/07/11`, and `pkg-g1` now includes `MAPSCREEN-011/012` with `MS-UX-05` and `MS-ENG-05` (`scripts/qa/capture_mapscreen_phase2_package_evidence.ps1`).
15. Threshold resolver hardening applied: `scripts/qa/verify_mapscreen_package_evidence.ps1` now selects the latest canonical timestamp run directory (`yyyyMMdd-HHmmss`) by default, avoiding accidental selection of non-canonical dry-run/manual folders.
16. Fresh phase-2 package runs generated against the tightened contract: `pkg-d1/20260305-170139`, `pkg-g1/20260305-170311`, and `pkg-w1/20260305-170441` (all include required-gate command evidence + connected app instrumentation pass in package artifacts).
17. Updated threshold rollup (`run_mapscreen_evidence_threshold_checks.ps1 -UpdateGateResults -AllowPending`) now reports: `pkg-d1 pending=4`, `pkg-g1 pending=2`, `pkg-w1 pending=1`, and `pkg-e1 failed=1 (MS-UX-01)`.
18. Phase-2 host perf evidence harness added for package SLO automation:
    - `feature/map/src/test/java/com/example/xcpro/map/MapscreenPkgD1PerfEvidenceTest.kt`
    - `feature/map/src/test/java/com/example/xcpro/map/MapscreenPkgG1PerfEvidenceTest.kt`
    - `feature/map/src/test/java/com/example/xcpro/map/MapscreenPkgW1PerfEvidenceTest.kt`
    - shared writer/support: `feature/map/src/test/java/com/example/xcpro/map/MapscreenPerfEvidenceSupport.kt`
    - JSON output path: `feature/map/build/reports/perf/mapscreen/<package>-evidence.json`
19. Phase-2 metric application automation added: `scripts/qa/apply_mapscreen_phase2_perf_metrics.ps1` now runs package evidence test(s), applies measured `slo_metrics` into `metrics.json` (tier_a + tier_b), marks SLO status as `captured`, and can call verifier gate update.
20. Threshold verifier strict-mode hardening applied for non-pending SLO evaluation: `scripts/qa/verify_mapscreen_package_evidence.ps1` now preserves rule/tier check collections as arrays (`+= ,(...)`) before `.Count` checks to avoid scalar pipeline failures once metrics are populated.
21. Applied measured phase-2 metrics to current package runs:
    - `pkg-d1/20260305-170139` -> `ready_for_promotion`
    - `pkg-g1/20260305-170311` -> `ready_for_promotion`
    - `pkg-w1/20260305-170441` -> `ready_for_promotion`
22. Latest threshold rollup (`run_mapscreen_evidence_threshold_checks.ps1 -UpdateGateResults -AllowPending`) now reports phase-2 green lanes (`pkg-d1/g1/w1` pass) with remaining blocker isolated to `pkg-e1` (`blocked_failed_thresholds`, `MS-UX-01`).
23. Strict completion contract run `20260305-193049` re-confirmed phase-5 blocker for `pkg-e1`:
    - Tier A: `p95=25 ms`, `p99=34 ms`, `jank=6.56%`
    - Tier B: `p95=25 ms`, `p99=34 ms`, `jank=6.74%`
    - promotion decision: `blocked_failed_thresholds` (`MS-UX-01`).
24. Focused runtime repass implemented additional interaction-cadence hardening and display-frame throttling:
    - delayed interaction deactivation window before deferred overlay flush (`MapOverlayManagerRuntime`),
    - increased interaction cadence floors (`MapOverlayInteractionCadencePolicy`),
    - live display-pose loop reduced to 40 fps target cadence (`MapComposeEffects`),
    - added cadence policy tests for deactivation delay contract.
25. Strict completion contract run `20260305-195205` after focused repass still fails at phase 5, but with improved Tier B p95:
    - Tier A: `p95=25 ms`, `p99=34 ms`, `jank=6.39%`
    - Tier B: `p95=24 ms`, `p99=34 ms`, `jank=6.21%`
    - promotion decision: `blocked_failed_thresholds` (`MS-UX-01`).
26. A time-boxed deviation entry was added for this mandatory SLO miss:
    - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` issue `RULES-20260305-12`
    - expiry `2026-04-15`
    - removal requires strict `pkg-e1` threshold pass and full completion contract success.
27. Deferred-fix decision recorded (2026-03-05):
    - `MS-UX-01` optimization is postponed to a follow-up pass,
    - current lane remains blocked only at `pkg-e1` phase-5 threshold gate,
    - next activation trigger: start focused `pkg-e1` cycle before `2026-04-15` and close `RULES-20260305-12`.
28. Focused `pkg-e1` follow-up runtime remediation landed:
    - traffic projection invalidation now uses a dedicated interaction-aware floor (`120 ms` base, `250 ms` while interaction is active) instead of always reusing the fixed `120 ms` projection window during pan/rotate,
    - deferred weather/OGN/ADS-B interaction-release work is now batched by `MapOverlayManagerRuntime` behind one short settle window before the final front-order reconcile,
    - targeted unit coverage was added for projection cadence and interaction-release flush sequencing across runtime, weather, OGN, and ADS-B owners,
    - next required step remains a fresh strict `pkg-e1` evidence run to determine whether `RULES-20260305-12` can be removed.
29. Phase-1 mixed-load fanout reduction landed:
    - root Compose traffic binding is now UI-only; hot OGN/ADS-B overlay lists and icon/config inputs moved to a dedicated runtime-only traffic overlay input seam,
    - traffic overlay mutation now runs from direct flow collectors instead of list-keyed Compose render-state effects,
    - overlay ownship altitude is now quantized upstream (`2 m`, `distinctUntilChanged`) before the runtime collector seam, so raw altitude jitter no longer wakes root Compose for overlay-only work,
    - next required step is a fresh strict `pkg-e1` Tier A/B evidence run to measure whether the remaining `MS-UX-01` miss is materially reduced before starting weather/front-order phase 2.
30. Collector-side churn fix landed on the phase-1 branch after Tier A regression evidence:
    - `MapTrafficOverlayRuntimeCollectors` now dedupes hot OGN traffic, ADS-B traffic, and OGN target-visual requests with render-relevant signatures before port updates,
    - runtime-owned overlay status counters now expose collector emissions, dedupe skips, and forwarded port updates for the new collector seam,
    - next required step is another strict `pkg-e1` Tier A/B run to determine whether the Tier A regression is recovered before opening weather/front-order phase 2.
31. Main-thread frame-production repass landed after Tier A trace investigation:
    - `MapScreenComposeAndLifecycleEffects` no longer collects hot `currentLocation` / `orientation` into root Compose state; `MapComposeEffects` now consumes those flows with collector-driven side effects,
    - `MapScreenContentRuntime` no longer collects hot `currentLocation` / `currentZoom` at the content root; the location/zoom reads were pushed down to narrow runtime/UI seams in `MapOverlayStack`, live-follow, task/action-button wrappers, and traffic detail-panel wrappers,
    - expected payoff is lower `Choreographer#doFrame` / `Recomposer:recompose` pressure on the phone-side mixed-load path before the next strict `pkg-e1` evidence run.
32. Render-sync repaint coalescing landed for the remaining Tier B frame-time miss:
    - `LocationManager` now routes render-sync repaint requests through a dedicated `DisplayPoseRepaintGate` instead of calling `triggerRepaint()` on every accepted orientation/fix update,
    - the repaint gate uses the same live (`25 ms`) and replay (`16.7 ms`) cadence contract as the Compose-owned display-pose loop and clears pending work once a render frame starts,
    - exactness paths stay immediate for lifecycle resume, ownship re-enable, and direct render-frame handling,
    - next required step is a fresh strict `pkg-e1` Tier A/B run to confirm Tier A stays green and Tier B frame time clears `MS-UX-01`.
33. Map host/render-frame queue tightening landed for the final Tier B-only miss:
    - `MapViewHost` now binds the render-frame listener once per `MapView` instance through a dedicated host-binding controller instead of rebinding from `AndroidView.update`,
    - `RenderFrameSync` now coalesces off-main pending `mapView.post { onRenderFrame() }` callbacks and clears stale pending work on rebind/unbind,
    - expected payoff is a narrower `MapView`/render-thread handoff on the weaker tier without reopening traffic/runtime ownership,
    - next required step is a fresh strict `pkg-e1` Tier A/B run to confirm the remaining Tier B `frame_time_p95_ms` miss is closed while Tier A stays green.
