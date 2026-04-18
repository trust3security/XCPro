# CHANGE_PLAN_OGN_REMAINING_GAPS_2026-03-01.md

## Purpose

Close the remaining post-hardening OGN gaps found in the latest code pass,
focused on:
- DDB refresh cadence efficiency
- OGN observability/debug coverage
- OGN overlay lifecycle regression protection
- DDB repository behavior test coverage

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN remaining gaps hardening plan
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: TBD
- Status: Implemented (Phases 1/2/3/4 complete)

## 0A) Implementation Update (2026-03-01)

Implemented now:
- Phase 1 complete:
  - `NotDue` now advances repository cadence anchor in
    `OgnTrafficRepository.requestDdbRefreshIfDue(...)`.
  - Added integration guard test:
    - `ddbRefreshNotDue_doesNotRelaunchEveryActiveCheckTick`
      in `OgnTrafficRepositoryConnectionTest`.
- Phase 2 complete:
  - OGN debug panel now renders dropped-frame counters:
    - `Drops (order/motion): <out-of-order>/<implausible-motion>`.
- Phase 4 complete:
  - Added dedicated `OgnDdbRepositoryTest` coverage for:
    - cache-based `NotDue` path (no network launch)
    - successful force refresh
    - HTTP failure classification
    - empty payload failure classification
- Phase 3 complete:
  - Added focused OGN overlay lifecycle regression tests in:
    - `MapOverlayManagerOgnLifecycleTest`
  - Added a minimal test seam in `MapOverlayManager` via injectable overlay factories
    to lock lifecycle behavior without runtime behavior drift.
  - Locked behavior:
    - render path one-time initialize (no per-render reinitialize for existing overlay)
    - style recreate cleanup/re-init with latest cached payload render
    - deferred render cancellation on map detach

Pending:
- Optional bounded self-heal fallback for unexpected style/source loss (performance-only hardening).

Code-pass update (2026-03-01, Phase 3 deep pass + implementation):
- Dedicated Phase 3 implementation plan created:
  - `docs/OGN/CHANGE_PLAN_OGN_PHASE3_OVERLAY_LIFECYCLE_REGRESSION_LOCK_2026-03-01.md`
- Phase 3 implementation confirms:
  - manager-owned fast-path is implemented and now regression-locked by tests
  - remaining improvement is optional bounded self-heal for unexpected style/source loss

## 1) Findings From Code Pass (2026-03-01)

1. DDB `NotDue` path does not advance repository-side cadence anchor.
- Evidence:
  - `lastDdbRefreshSuccessWallMs == Long.MIN_VALUE` gate remains true
    (`feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt:942`).
  - `OgnDdbRefreshResult.NotDue -> Unit` with no state update
    (`feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt:960`).
- Impact:
  - Causes unnecessary periodic refresh launch attempts during active sessions.
  - Functionally correct, but avoidable CPU wakeups and scheduling churn.

2. OGN debug panel does not surface dropped-frame counters.
- Evidence:
  - Snapshot already includes counters:
    `droppedOutOfOrderSourceFrames`, `droppedImplausibleMotionFrames`
    (`feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt:32`).
  - `OgnDebugPanel` currently does not render those fields
    (`feature/map/src/main/java/com/trust3/xcpro/map/ui/MapTrafficDebugPanels.kt`).
- Impact:
  - Harder field diagnosis when pilots report icon jitter/filtering behavior.

3. OGN overlay fast-path has no focused lifecycle regression tests.
- Evidence:
  - Existing map overlay tests include airspace/weather and a trail render policy test,
    but no dedicated OGN traffic/thermal/trail lifecycle test lock:
    `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerAirspaceTest.kt`,
    `feature/map/src/test/java/com/trust3/xcpro/map/OgnGliderTrailOverlayRenderPolicyTest.kt`.
- Impact:
  - Future map/style lifecycle refactors can accidentally reintroduce render-time
    initialization overhead or break style-recreate behavior.

4. No direct repository tests for DDB refresh repository behavior.
- Evidence:
  - OGN tests include parser and traffic repository tests, but no
    `OgnDdbRepository` behavior tests.
- Impact:
  - Failure-mode classification (HTTP/non-2xx/empty payload/parse) and cache
    timestamp semantics are not locked by unit tests.

## 2) Scope

- In scope:
  - OGN DDB cadence anchor fix (`NotDue` handling).
  - OGN debug panel observability enrichment (existing snapshot counters).
  - OGN overlay lifecycle regression tests.
  - OGN DDB repository unit tests.
- Out of scope:
  - New OGN product features.
  - Protocol or UI redesign.
  - ADS-B behavior changes.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| DDB refresh cadence state | `OgnTrafficRepository` | repository internal state + snapshot-derived age | ViewModel/UI timers |
| Dropped-frame diagnostics | `OgnTrafficRepository` | `OgnTrafficSnapshot` | ad-hoc UI counters |
| Overlay init lifecycle state | `MapOverlayManager` + overlay runtime | map runtime internals | ViewModel-side lifecycle flags |

### 3.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

No new cross-layer shortcuts.

### 3.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| DDB cadence anchor (`lastDdbRefreshSuccessWallMs`) | Wall | refresh due-check cadence |
| DDB failure retry schedule | Monotonic | resilient short retry window |
| Overlay render cadence | Monotonic | UI throttling and scheduling |

No wall/monotonic cross-subtraction in new logic.

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  - Add failing/expectation tests before behavior changes.
- Files:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/*` (new OGN lifecycle tests)
- Exit:
  - Tests reproduce current `NotDue` cadence behavior and lifecycle assumptions.

### Phase 1 - DDB `NotDue` cadence anchor fix

- Goal:
  - Prevent repeated unnecessary refresh-launch attempts when DDB is not due.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
- Change:
  - On `OgnDdbRefreshResult.NotDue`, advance repository cadence anchor
    (`lastDdbRefreshSuccessWallMs`) using a safe wall-time value.
- Tests:
  - Add/extend connection test to verify cadence anchoring after `NotDue`.
- Exit:
  - `NotDue` does not trigger minute-by-minute launch churn.

### Phase 2 - OGN observability uplift

- Goal:
  - Surface existing dropped-frame counters in OGN debug panel.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapTrafficDebugPanels.kt`
  - tests: `feature/map/src/test/java/com/trust3/xcpro/map/ui/*`
- Change:
  - Render `droppedOutOfOrderSourceFrames` and `droppedImplausibleMotionFrames`.
- Exit:
  - Operators can distinguish source-order drops vs motion-plausibility drops.

### Phase 3 - Overlay lifecycle regression lock

- Goal:
  - Lock fast-path initialization behavior and style-reload correctness.
- Detailed implementation plan:
  - `docs/OGN/CHANGE_PLAN_OGN_PHASE3_OVERLAY_LIFECYCLE_REGRESSION_LOCK_2026-03-01.md`
- Files:
  - new tests under `feature/map/src/test/java/com/trust3/xcpro/map/`
    (for traffic/thermal/trail overlay lifecycle and manager wiring)
- Change:
  - Add tests covering:
    - render path does not invoke init path per update
    - style recreation still initializes once and renders correctly
    - map detach clears pending render jobs safely
- Exit:
  - OGN overlay fast-path guarded against regressions.

### Phase 4 - DDB repository behavior test coverage

- Goal:
  - Lock DDB refresh behavior directly in repository tests.
- Files:
  - new `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnDdbRepositoryTest.kt`
- Change:
  - Add tests for:
    - `NotDue` vs `Updated` semantics
    - HTTP failure classification
    - parse/empty payload failure handling
    - cache timestamp load semantics
- Exit:
  - DDB behavior protected without relying only on traffic repo integration tests.

### Phase 5 - Verification and rescore

- Goal:
  - Release-grade validation and quality score update.
- Required checks:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Focused reruns:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.ogn.*" --rerun-tasks`
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.*Ogn*" --rerun-tasks`

## 5) Acceptance Gates

- No architecture/rule violations.
- No timebase misuse.
- DDB `NotDue` cadence anchor behavior verified by tests.
- OGN debug panel includes dropped-frame diagnostics.
- OGN overlay lifecycle fast-path has dedicated regression coverage.
- OGN score target:
  - connectivity reliability: `>=95/100`
  - overall OGN slice quality: `>=96/100`

## 6) Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Over-anchoring `NotDue` hides true due window | delayed metadata refresh | anchor using current wall time only on explicit `NotDue`, keep failure retry path unchanged |
| Extra debug text increases panel noise | lower readability | compact formatting, debug-only scope unchanged |
| Overlay tests brittle around map SDK behavior | flaky CI | keep tests at policy/lifecycle boundary, avoid map-SDK internals |

## 7) Rollback Plan

1. Revert Phase 3/2 test/UI changes independently if needed.
2. Revert Phase 1 cadence fix independently if field behavior regresses.
3. Keep added tests when possible to preserve failure evidence.
4. Re-run required checks before merge.
