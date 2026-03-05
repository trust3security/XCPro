# MapScreen Workstreams and Phase Gates

Date: 2026-03-05  
Owner: XCPro Team  
Status: Draft

## 1) Workstream A - Task Drag Render Pipeline

Problem:
- AAT drag move path can trigger full task overlay clear/remove/replot per move.

Deliverables:
1. Introduce drag-preview path that updates only necessary task artifacts during drag.
2. Keep full task sync for commit/end-of-gesture and structural task mutations.
3. Add coalescing/throttling to avoid redundant syncs inside a single render interval.

Tests:
- drag stress test (continuous drag) with sync-count assertions.
- regression test ensuring final committed geometry remains correct.

Gate:
- no full task teardown per move event during drag (`MS-UX-02`).

## 2) Workstream B - Weather/Traffic Layer Ordering

Problem:
- rain animation frame updates can repeatedly invoke traffic/ownship `bringToFront` churn.

Deliverables:
1. centralize ordering in one idempotent runtime policy.
2. avoid remove/add layer operations unless ordering is actually dirty.
3. verify blue-location and traffic overlay visibility invariants.

Tests:
- layer ordering policy tests (weather + traffic + ownship).
- animation tick test asserting no repeated redundant reorder operations.

Gate:
- ordering operations occur only on real state transitions (`MS-UX-04`).

## 3) Workstream C - Startup Overlay Idempotency

Problem:
- duplicated overlay reapply during map init and map-ready callback.

Deliverables:
1. single owner for startup overlay reapply sequence.
2. map-generation guard for one-time startup apply.
3. startup telemetry counter to prevent regressions.

Tests:
- startup initialization test asserting one reapply pass per generation.

Gate:
- startup overlay apply is exactly-once per generation (`MS-UX-06`, `MS-ENG-02`).

## 4) Workstream D - ADS-B and OGN Hot Path Optimization

Problem:
- ADS-B frame path duplicates frame-list traversal.
- OGN publish path sorts full target list every publish.

Deliverables:
1. ADS-B: compute frame target list once and reuse in the same frame.
2. OGN: replace unconditional sort with conditional/incremental ordering policy.
3. preserve output ordering determinism.

Tests:
- ADS-B animation frame micro-benchmark tests.
- OGN publish ordering/determinism tests.

Gate:
- measured reduction in hot-path cost with no behavior drift (`MS-ENG-03`, `MS-ENG-04`).

## 5) Workstream E - Stress Hardening and Governance

Problem:
- mixed map motion + overlay animation + drag edits can cause bursty update pressure.

Deliverables:
1. define cadence governance for high-frequency visual updates.
2. add bounded coalescing/backpressure where safe.
3. add automated threshold verification for package evidence (`metrics.json` -> `gate_result.json`).
4. document and test contention scenarios.

Tests:
- mixed-load stress harness scenario.
- replay determinism checks unchanged.

Gate:
- p95/p99 movement SLOs stable in mixed-load scenarios (`MS-UX-01`).

## 6) Workstream F - Lifecycle and State Fanout Control

Problem:
- map-view binding/update path can trigger repeated lifecycle sync work.
- ownship altitude fanout can drive frequent overlay updates and deep equality checks.

Deliverables:
1. make map-view bound lifecycle sync one-shot/idempotent for the active owner state.
2. remove recomposition-driven `onDisplayFrame` side effects from lifecycle sync path.
3. add ownship-altitude cadence policy for overlay paths (quantization/throttle and
   correctness bounds).
4. reduce deep list equality work in hot paths (version/signature-based guards where safe).

Tests:
- map-view host update test ensuring lifecycle sync is not repeatedly invoked on recomposition.
- overlay update cadence tests for ownship altitude changes.
- regression tests preserving label correctness and replay determinism.

Gate:
- no recomposition-driven lifecycle/display-frame thrash.
- lower ownship-altitude-driven overlay churn without behavior regression (`MS-ENG-06`).

## 7) Workstream G - Replay Observer and SCIA Trail Pipeline Efficiency

Problem:
- replay session progress updates can trigger avoidable observer recompute work.
- SCIA trail path currently performs expensive full-list operations in multiple stages.

Deliverables:
1. add `distinctUntilChanged` to replay-selection projection used by map observer combine path.
2. gate replay session diagnostic logging to debug/feature-flag/rate-limited policy only.
3. optimize SCIA trail path:
   - avoid repeated deep list equality on large segment lists,
   - reduce full-list filter + full-geojson rebuild frequency,
   - preserve deterministic output ordering.
4. remove avoidable per-target allocations in OGN selection checks (`setOf(...)` in loops).

Tests:
- observer recompute tests ensuring session-progress-only updates do not trigger no-op recomputation.
- SCIA dense segment stress tests for render cadence and output parity.
- selection matching tests preserving canonical key behavior after allocation optimizations.

Gate:
- replay observer no-op churn reduced and logs bounded (`MS-UX-05`).
- SCIA dense-path cost materially reduced with unchanged visible behavior (`MS-ENG-05`).

## 8) Visual SLO Gates (Mandatory)

All MapScreen workstreams must declare impacted SLO IDs from
`docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
and attach evidence per
`docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`.

| Phase | Mandatory SLO IDs | Gate Criteria |
|---|---|---|
| Phase 0 | all `MS-UX-*`, all `MS-ENG-*` | baseline captured and reproducible across required scenarios |
| Phase 1 | `MS-UX-02`, `MS-UX-04`, `MS-UX-06`, `MS-ENG-06`, `MS-ENG-10` | all thresholds pass in drag/order/startup/lifecycle/cadence scenarios |
| Phase 2 | `MS-UX-03`, `MS-UX-05`, `MS-ENG-01/03/04/05/07/08/09/11` | all thresholds pass in traffic/replay/SCIA/cache/fanout hot paths |
| Phase 3 | `MS-UX-01` + no regression on prior SLOs | mixed-load stress passes with p95/p99 and jank targets |
| Phase 4 | all impacted SLO IDs | merge blocked unless all pass or approved deviation exists |

## 9) Phase-by-Phase Exit Gates

### Phase 0 Gate
- baseline metrics captured across all required scenarios.

### Phase 1 Gate
- Workstreams A/B/C/F implemented and verified.
- major churn causes removed and mandatory Phase 1 SLOs passed.

### Phase 2 Gate
- Workstreams D/G implemented and verified.
- hotspot CPU improvements measured and mandatory Phase 2 SLOs passed.

### Phase 3 Gate
- Workstream E implemented and validated under stress with movement SLO pass.

### Phase 4 Gate
- required checks and final quality rescore complete.
- all impacted SLO IDs pass, or explicit approved deviation exists.

## 10) Risk Notes Per Workstream

| Workstream | Main Risk | Mitigation |
|---|---|---|
| A | drag-preview path diverges from commit path | strict final-sync test + geometry parity assertions |
| B | layer ordering regressions hide overlays | centralized ordering contract + policy tests |
| C | startup sequence race conditions | map-generation guard + integration tests |
| D | optimization changes output semantics | deterministic ordering tests + snapshot validation |
| E | over-throttling harms responsiveness | enforce latency SLO and interactive test coverage |
| F | cadence/quantization could stale label updates | set explicit correctness thresholds and test relative-altitude display behavior |
| G | SCIA optimizations could alter trail continuity | parity tests for trail ordering/visibility and replay determinism checks |

## 11) Execution Ledger (Mandatory)

| Package ID | Workstream | Owner Role | Depends On | Entry Criteria | Exit Criteria | Artifact |
|---|---|---|---|---|---|---|
| PKG-A1 | A | task runtime owner | Phase 0 baseline | drag teardown baseline captured | `MS-UX-02` pass, teardown-per-move = 0 | `artifacts/mapscreen/phase1/pkg-a1/<timestamp>/` |
| PKG-B1 | B | overlay runtime owner | PKG-A1 | reorder baseline captured | `MS-UX-04` pass, redundant reorder = 0 | `artifacts/mapscreen/phase1/pkg-b1/<timestamp>/` |
| PKG-C1 | C | map init owner | PKG-B1 | startup apply baseline captured | `MS-UX-06` and `MS-ENG-02` pass | `artifacts/mapscreen/phase1/pkg-c1/<timestamp>/` |
| PKG-F1 | F | lifecycle/runtime owner | PKG-C1 | lifecycle/cadence/recomposition counters enabled | `MS-ENG-06`, `MS-ENG-09`, and `MS-ENG-10` pass | `artifacts/mapscreen/phase1/pkg-f1/<timestamp>/` |
| PKG-D1 | D | ADS-B/OGN owner | PKG-F1 | ADS-B/OGN hotpath baseline captured | `MS-ENG-03`, `MS-ENG-04`, `MS-ENG-07`, `MS-ENG-11` pass | `artifacts/mapscreen/phase2/pkg-d1/<timestamp>/` |
| PKG-G1 | G | replay/trail owner | PKG-F1 | replay + SCIA baseline captured | `MS-UX-05`, `MS-ENG-05` pass | `artifacts/mapscreen/phase2/pkg-g1/<timestamp>/` |
| PKG-W1 | B/F | weather owner | PKG-F1 | rain cache baseline captured | `MS-ENG-08` pass | `artifacts/mapscreen/phase2/pkg-w1/<timestamp>/` |
| PKG-E1 | E | performance owner | PKG-D1, PKG-G1, PKG-W1 | all phase 2 packages green | `MS-UX-01` pass + no regression | `artifacts/mapscreen/phase3/pkg-e1/<timestamp>/` |
| PKG-R1 | release | release owner | all prior packages | all mandatory artifacts present | all impacted SLO IDs pass | `artifacts/mapscreen/phase4/pkg-r1/<timestamp>/` |

## 12) Stop/Go Policy (Mandatory)

1. `Stop` phase promotion if any impacted mandatory SLO is failing on either device tier.
2. `Stop` phase promotion if any package artifact is missing.
3. `Stop` phase promotion if replay determinism parity fails (`DET-*` invariant break).
4. `Go` only when all package exit criteria and artifact contracts are satisfied.
5. `Go with exception` only via approved `KNOWN_DEVIATIONS.md` entry with issue ID, owner, expiry, mitigation, and rollback.
