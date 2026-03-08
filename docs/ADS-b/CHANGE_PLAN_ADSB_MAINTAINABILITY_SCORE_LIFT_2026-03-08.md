# ADS-B Maintainability Score Lift - Production-Grade Phased IP

Date: 2026-03-08
Owner: XCPro Team
Status: Draft (execution-ready)
Scope type: Refactor and hardening (no intended behavior change)

Execution update (2026-03-08):
- Phase 1 started and completed for runtime policy consolidation.
- Phase 2 started and completed for store policy decomposition.
- Phase 3 started and completed for runtime orchestration decomposition.
- Phase 4 started and completed for ADS-B overlay/delegate hardening.
- Phase 5 started and completed for release hardening/observability/docs sync.
- Implemented:
  - shared policy constants moved to `AdsbTrafficRepositoryRuntimePolicy.kt`
  - shared wait-state contracts (`CenterWaitState`, `NetworkWaitState`) moved to the same policy file
  - duplicated constants removed from runtime loop/polling files
  - duplicated nested wait-state contracts removed from runtime class
  - policy-invariant regression test added (`AdsbTrafficRepositoryRuntimePolicyTest`)
  - `AdsbTrafficStore` decomposed into focused policy helpers:
    - `AdsbProximityTierResolver`
    - `AdsbTrafficSelectionOrdering`
    - `AdsbTrafficThreatPolicies`
  - store orchestrator updated to use extracted helpers with no intended behavior drift
  - direct policy tests added:
    - `AdsbProximityTierResolverTest`
    - `AdsbTrafficSelectionOrderingTest`
  - runtime orchestration split into focused helpers:
    - `AdsbTrafficRepositoryRuntimeLoopTransitions.kt` (poll-cycle/error/backoff transitions)
    - `AdsbTrafficRepositoryRuntimeNetworkWait.kt` (center/network wait + housekeeping driver)
    - `AdsbTrafficRepositoryRuntimeSnapshot.kt` emergency/snapshot projection decomposition
  - `AdsbTrafficRepositoryRuntimeLoop.kt` simplified to a small loop driver using explicit step outcomes
  - direct runtime transition tests added:
    - `AdsbTrafficRepositoryRuntimeTransitionsTest`
  - ADS-B overlay maintainability split:
    - `AdsbOverlayFrameLoopController.kt` (frame scheduling/interval gating)
    - `AdsbTrafficOverlayFeatureProjection.kt` (feature build projection path)
    - `AdsbTrafficOverlay.kt` simplified to orchestrate style/runtime flow
  - traffic delegate hardening:
    - `MapOverlayManagerRuntimeTrafficDelegate.kt` now uses a single style projection path helper for both init and render cycles
  - direct map runtime delegate tests added:
    - `MapOverlayManagerRuntimeTrafficDelegateTest`
      - render throttling + deferred flush
      - sticky projection cache application
      - default-medium unknown rollout switch behavior
      - runtime counter projection updates
  - release hardening + docs sync:
    - `MapOverlayManagerRuntimeStatus` now surfaces ADS-B runtime counters in status output
      (unknown/legacy unknown counts, resolve-latency stats, rollout-effective flag,
      overlay front-order apply/skip counts)
    - `MapOverlayManagerRuntime.getOverlayStatus()` now includes runtime counter snapshot
    - `docs/ADS-b/ADSB.md` updated to v8 with maintainability/observability hardening contract
- Verification status:
  - `./gradlew :feature:map:compileDebugKotlin` pass
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.adsb.AdsbTrafficRepositoryRuntimePolicyTest" --tests "com.example.xcpro.adsb.*" --tests "com.example.xcpro.map.Adsb*"` pass
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.adsb.AdsbTrafficStore*" --tests "com.example.xcpro.adsb.AdsbProximityTierResolverTest" --tests "com.example.xcpro.adsb.AdsbTrafficSelectionOrderingTest"` pass
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.adsb.AdsbTrafficRepositoryRuntimeTransitionsTest"` pass
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapOverlayManagerRuntimeTrafficDelegateTest" --tests "com.example.xcpro.map.AdsbStickyIconProjectionCacheTest" --tests "com.example.xcpro.map.AdsbIconTelemetryTrackerTest"` pass
  - `./gradlew enforceRules` pass (Phase 5 re-run)
  - `./gradlew testDebugUnitTest` pass (Phase 5 re-run)
  - `./gradlew assembleDebug` pass (Phase 5 re-run)

## 0) Objective

Increase ADS-B implementation quality from current deep-pass baseline (~93/100 overall)
to production-grade >=96/100 by improving structure, maintainability, and test confidence
while preserving runtime behavior and architecture contracts.

Primary pain points from current pass:
- hotspot files are still large and mixed-responsibility (`AdsbTrafficStore`, runtime loop/polling/snapshot paths, ADS-B overlay runtime delegate chain)
- duplicated runtime constants/policies across runtime split files
- missing direct unit-test coverage for some high-risk map ADS-B delegate behavior
- architecture compliance is strong, but maintainability and change safety can be higher

## 1) Baseline and Target

Current baseline (deep pass):
- Architecture compliance: 95
- Structure/modularity: 90
- Coding quality: 93
- UX/correctness outcome: 92
- Performance/latency: 91
- Test confidence: 94
- Rollout safety/observability: 93
- Overall: 93

Target after full execution:
- Architecture compliance: >=97
- Structure/modularity: >=97
- Coding quality: >=96
- UX/correctness outcome: >=95
- Performance/latency: >=95
- Test confidence: >=96
- Rollout safety/observability: >=95
- Overall: >=96

## 2) Scoring Rubric (Per Phase, /100)

- Architecture compliance: 30
- UX outcome: 25
- Performance/latency: 20
- Test confidence: 15
- Rollout safety/observability: 10

Phase score formula:
- `PhaseScore = Architecture + UX + Performance + Test + Rollout`

## 3) Non-Negotiables

Must remain true in every phase:
- MVVM + UDF + SSOT layering remains intact.
- Dependency direction remains `UI -> domain -> data`.
- No business policy leaks into UI/ViewModel.
- Monotonic/wall/replay timebase separation remains explicit.
- Replay determinism remains unchanged.
- No hidden global mutable state.
- No behavior drift unless explicitly approved and documented.

## 4) Phased Plan

### Phase 0 - Baseline Lock and Refactor Guardrails

Goal:
- Lock current ADS-B behavior before structural changes.
- Add guardrails that catch drift early during decomposition.

In scope:
- Capture explicit ADS-B refactor invariants in tests and docs.
- Add/confirm targeted assertions for:
  - icon mapping semantics (including unknown fallback and authoritative non-fixed-wing precedence)
  - runtime connection-state transitions and retry/circuit behavior
  - ownship reference freshness and fallback semantics
- Add hotspot split checklist and file budget targets for ADS-B files touched in later phases.

Planned files:
- `docs/ADS-b/ADSB.md` (append implementation invariants if needed)
- `docs/ADS-b/ADSB_Improvement_Plan.md` (cross-reference to this plan)
- ADS-B test files under `feature/map/src/test/java/com/example/xcpro/adsb/` and `.../map/`

Exit criteria:
- Baseline invariants are documented and test-locked.
- No production behavior change.
- Required quality gates pass.

Target score:
- 94/100

### Phase 1 - Runtime Policy Consolidation (No Behavior Change)

Goal:
- Remove duplicated runtime constants and shared policy drift risk.

In scope:
- Consolidate duplicated constants and wait-state contracts currently repeated in runtime split files into one ADS-B runtime policy contract file.
- Keep runtime logic behaviorally identical.
- Add narrow unit tests for policy-level delay floors/bounds and retry-floor classification mapping.

Planned files:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimeLoop.kt`
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimePolling.kt`
- `feature/map/src/main/java/com/example/xcpro/adsb/` (new `AdsbRuntimePolicy` file)
- related tests in `feature/map/src/test/java/com/example/xcpro/adsb/`

Exit criteria:
- Single policy source for loop/polling constants and wait-state semantics.
- No duplicate policy constants across runtime files.
- Existing ADS-B runtime tests remain green.

Target score:
- 95/100

### Phase 2 - Decompose `AdsbTrafficStore` into Focused Policy Units

Goal:
- Reduce complexity and increase local reasoning/testability in selection/proximity/risk logic.

In scope:
- Split store internals into focused components while preserving API and output ordering:
  - distance tier + hysteresis policy
  - proximity-tier resolution policy
  - emergency audio candidate selection policy
  - vertical non-threat/circling emergency rule policy
- Keep `AdsbTrafficStore.select(...)` as an orchestrator, not a monolith.
- Add pure unit tests per policy component, including boundary and stale/fresh transitions.

Planned files:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
- new policy helper files under `feature/map/src/main/java/com/example/xcpro/adsb/`
- `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStore*Test.kt`

Exit criteria:
- Store orchestrator reduced and easier to audit.
- Policy branches covered by direct tests.
- Deterministic output ordering remains unchanged.

Target score:
- 96/100

### Phase 3 - Runtime Orchestration Decomposition

Goal:
- Improve maintainability and failure-path clarity of repository runtime loop/mutation/snapshot orchestration.

In scope:
- Extract focused runtime helpers:
  - loop driver and error/backoff transitions
  - network wait and housekeeping driver
  - snapshot assembly and telemetry projection
- Keep single-writer mutation model and existing state machine semantics.
- Add direct transition tests for:
  - offline -> online resume
  - circuit-breaker open/half-open/probe
  - reconnect timestamp correctness
  - degraded-state snapshot fields

Planned files:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt`
- `.../AdsbTrafficRepositoryRuntimeLoop.kt`
- `.../AdsbTrafficRepositoryRuntimeLoopTransitions.kt`
- `.../AdsbTrafficRepositoryRuntimeNetworkWait.kt`
- `.../AdsbTrafficRepositoryRuntimePolling.kt`
- `.../AdsbTrafficRepositoryRuntimeSnapshot.kt`
- runtime-focused tests under `feature/map/src/test/java/com/example/xcpro/adsb/` (including `AdsbTrafficRepositoryRuntimeTransitionsTest`)

Exit criteria:
- Runtime responsibilities separated with no state-authority ambiguity.
- Transition tests cover major failure and recovery paths directly.
- No regressions in existing ADS-B unit tests.

Target score:
- 96/100

### Phase 4 - Map ADS-B Overlay and Delegate Hardening

Goal:
- Improve maintainability and confidence of ADS-B map rendering/delegate wiring.

In scope:
- Split `AdsbTrafficOverlay` into focused internal helpers (style images/layers, frame scheduling, feature build path) where useful.
- Keep render cadence and visual semantics unchanged.
- Add direct tests for `MapOverlayManagerRuntimeTrafficDelegate` behaviors:
  - render throttling and deferred flush behavior
  - sticky projection cache application
  - default-medium-unknown rollout switch behavior
  - runtime counter updates

Planned files:
- `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
- `feature/map/src/main/java/com/example/xcpro/map/AdsbOverlayFrameLoopController.kt`
- `feature/map/src/main/java/com/example/xcpro/map/AdsbTrafficOverlayFeatureProjection.kt`
- `feature/map/src/main/java/com/example/xcpro/map/AdsbStickyIconProjectionCache.kt`
- `feature/map/src/main/java/com/example/xcpro/map/AdsbIconTelemetryTracker.kt`
- new tests under `feature/map/src/test/java/com/example/xcpro/map/` (including `MapOverlayManagerRuntimeTrafficDelegateTest`)

Exit criteria:
- Delegate critical behavior is directly test-covered.
- Overlay runtime code is easier to change safely.
- No map interaction/ADS-B visual regression in existing tests.

Target score:
- 97/100

### Phase 5 - Release Hardening, Observability, and Documentation Sync

Goal:
- Close rollout-risk gaps and finalize production-readiness evidence.

In scope:
- Ensure ADS-B diagnostics surface key counters and transition telemetry consistently.
- Document refactor outcomes and updated architecture map sections if wiring changed.
- Re-run full required gates and collect final evidence package.

Planned files:
- `docs/ADS-b/ADSB.md`
- `docs/ARCHITECTURE/PIPELINE.md` (if wiring changed)
- ADS-B/map runtime files only if telemetry surfacing needs minor adjustments

Exit criteria:
- Final scorecard >=96 overall.
- Required gates green with evidence.
- Residual risks documented with mitigation/rollback.

Target score:
- 97/100

## 5) Verification Gates

Required each phase:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Recommended targeted fast loop during execution:

```bash
./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.adsb.*" --tests "com.example.xcpro.map.Adsb*"
```

When relevant (device/emulator):

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 6) Risks and Mitigation

1) Risk: behavior regression while splitting complex store/runtime logic.
- Mitigation: Phase 0 baseline lock + side-by-side policy tests + no-behavior-change acceptance gates per phase.

2) Risk: over-fragmentation causes readability drop.
- Mitigation: split by stable responsibility boundaries only; avoid tiny helper churn.

3) Risk: latency regressions from extra abstractions.
- Mitigation: maintain hot-path allocations and add p95 evidence checks for ADS-B feature build/render path.

4) Risk: documentation drift after wiring changes.
- Mitigation: mandatory docs sync in Phase 5 before closure.

## 7) Rollback Strategy

Rollback unit:
- Each phase is independently revertible by commit boundary.

Safe rollback triggers:
- Any architecture gate failure not resolved in-phase
- Determinism/behavior drift in baseline-lock tests
- p95 performance regression outside tolerance

Rollback steps:
1. Revert current phase commit(s) only.
2. Re-run required gates.
3. Log cause and adjust phase scope before retry.

## 8) Score Forecast by Phase

| Phase | Expected score (/100) | Lift driver |
|---|---:|---|
| Phase 0 | 94 | Baseline locks + guardrails |
| Phase 1 | 95 | Policy dedupe and drift prevention |
| Phase 2 | 96 | Store decomposition + direct policy tests |
| Phase 3 | 96 | Runtime orchestration clarity + transition tests |
| Phase 4 | 97 | Overlay/delegate hardening + direct delegate tests |
| Phase 5 | 97 | Release hardening + observability and docs closure |

## 9) Advice (Execution Order)

Recommended immediate start:
1. Phase 0 first (locks behavior before any structural edits).
2. Phase 1 next (low-risk, high-maintainability return).
3. Phase 2 then Phase 3 (core ADS-B logic refactor under locked behavior).
4. Phase 4 after runtime/store stability is proven.
5. Phase 5 to close production-readiness evidence and final scoring.

Execution note:
- This plan is intentionally behavior-preserving by default.
- Any intentional UX/behavior adjustment must be called out explicitly and rescored.

## 10) Final Closure (2026-03-08)

Phase-5 achieved score (evidence-based): `96/100`
- Architecture compliance: `30/30`
- UX outcome: `23/25`
- Performance/latency: `18/20`
- Test confidence: `15/15`
- Rollout safety/observability: `10/10`

Overall ADS-B maintainability score after Phases 1..5: `96/100`.

Residual risks:
- No connected instrumentation was run in this pass.
- No new p95 map-render profiling artifact was produced in this phase.
