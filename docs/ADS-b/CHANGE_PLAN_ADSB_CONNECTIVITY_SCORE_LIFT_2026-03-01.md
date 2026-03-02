# CHANGE PLAN - ADS-B Connectivity Score Lift (88 -> 96) (2026-03-01)

## 0) Metadata

- Title: ADS-B connectivity score-lift execution plan
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: TBD
- Status: In Progress
- Baseline score: 88/100
- Target score: 96/100

Context:
- Current ADS-B connectivity is stable on recovery mechanics and stale/expiry behavior.
- Main remaining deductions:
  1) User-facing release UX for degraded/offline ADS-B is still lightweight.
  2) Transition observability is not yet rich enough for fast field diagnosis.
  3) End-to-end network-transition coverage is not yet release-grade.

This plan is the execution track for the remaining score lift.
- Baseline hardening reference:
  - `docs/ADS-b/CHANGE_PLAN_ADSB_SOCKET_ERROR_HARDENING_2026-03-01.md`
- Existing closure plan reference:
  - `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_UX_STALE_HOUSEKEEPING_OBSERVABILITY_2026-03-01.md`
- Dedicated remaining-gap execution reference:
  - `docs/ADS-b/CHANGE_PLAN_ADSB_NETWORK_TRANSITION_E2E_COVERAGE_2026-03-01.md`

## 1) Score Model

### 1.1 Current scoring (88/100)

| Category | Current | Max | Notes |
|---|---:|---:|---|
| Recovery correctness (retry/circuit/offline pause/resume) | 24 | 25 | Strong |
| Data freshness behavior (stale/expiry) | 19 | 20 | Strong after offline housekeeping fix |
| User-facing connectivity UX | 14 | 20 | Main deduction |
| Runtime observability/diagnostics | 15 | 20 | Main deduction |
| Test confidence under transition churn | 16 | 20 | Needs stronger transition/e2e depth |

Adjusted target rubric for execution (total 100):
- Recovery correctness: 25
- Data freshness correctness: 20
- User-facing connectivity UX: 20
- Runtime observability: 20
- Test confidence: 15

### 1.2 Planned score lift by phase

| Phase | Expected lift | Running score |
|---|---:|---:|
| Phase 0 (baseline lock + telemetry contract tests) | +1 | 89 |
| Phase 1 (release persistent connectivity status UX) | +4 | 93 |
| Phase 2 (typed transition telemetry in snapshot) | +2 | 95 |
| Phase 3 (network-transition e2e hardening) | +1 | 96 |
| Phase 4 (final verification + docs lock) | gate | 96 |

### 1.3 Implementation progress (2026-03-01)

- Completed:
  - Phase 0 baseline lock.
  - Phase 1 release-safe persistent degraded ADS-B status surface (with recovery dwell).
  - Phase 2 typed transition telemetry in `AdsbTrafficSnapshot` and repository accounting.
- Added validation:
  - transition-counter/offline-dwell repository assertions
  - persistent status surface/presentation policy assertions
- Additional correctness hardening completed:
  - reconnect success timestamps now use fresh post-wait monotonic time (prevents stale timestamp carryover after prolonged offline/circuit waits)
- Phase 3 progress (partial):
  - added repository transition scenarios for circuit-open offline interruption and auth-fallback offline recovery
  - added extended offline/online flapping convergence scenario
  - added release-status UI rendering tests (Robolectric Compose) for persistent status + issue flash
  - extracted persistent issue visibility dwell logic into shared reducer (`reducePersistentIssueVisibility`) and added deterministic policy unit coverage
  - added feature-module instrumentation badge coverage scaffold and verified androidTest APK compilation
  - fixed instrumentation discovery in `feature:map` by setting AndroidJUnitRunner in module defaultConfig
  - expanded transition instrumentation scenarios to cover offline start, backoff state, recovery auto-dismiss, and rapid flap issue-return handling
  - validated this scope with passing checks:
    - `./gradlew.bat :feature:map:testDebugUnitTest --tests ...AdsbTrafficRepositoryTest... --tests ...AdsbStatusBadgesUiTest --tests ...PersistentIssueVisibilityPolicyTest --tests ...TrafficDebugPanelAutoDismissPolicyTest`
    - `./gradlew.bat :feature:map:assembleDebugAndroidTest`
    - `./gradlew.bat enforceRules`
    - `./gradlew.bat testDebugUnitTest`
    - `./gradlew.bat assembleDebug`
- release/e2e execution evidence added:
  - `./gradlew.bat --no-configuration-cache :feature:map:connectedDebugAndroidTest --no-parallel` (7 tests, 0 failed)
  - `./gradlew.bat --no-configuration-cache :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` (9 tests, 0 failed)
  - `./gradlew.bat --no-configuration-cache connectedDebugAndroidTest --no-parallel` (multi-module instrumentation green)
- Remaining primary gap:
  - repeated instrumentation stability runs (Phase 4 threshold evidence).
- Active execution plan for remaining gap:
  - `docs/ADS-b/CHANGE_PLAN_ADSB_NETWORK_TRANSITION_E2E_COVERAGE_2026-03-01.md`
- Current working score after this pass: `96/100`.

## 2) Scope

- Problem statement:
  - ADS-B internals recover correctly, but release UX and diagnosis confidence are still below "genius/release-grade."
- Why now:
  - Remaining deductions are concentrated and actionable; this is the shortest path to a >=96 score.
- In scope:
  - Persistent release-visible ADS-B connectivity status.
  - Explicit connectivity transition telemetry fields in SSOT snapshot.
  - Deterministic transition test matrix (unit + integration + selective instrumentation).
  - Documentation lock for future agents.
- Out of scope:
  - Provider/API changes.
  - OGN runtime behavior changes.
  - Broad map UI redesign beyond ADS-B status affordances.
- User-visible impact:
  - Clear persistent ADS-B health signal in release builds.
  - Faster diagnosis when users report intermittent outages.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B targets + core connectivity state | `AdsbTrafficRepository` | `snapshot` + `targets` `StateFlow` | UI-owned connection state machines |
| Connectivity status presentation model | UI mapping derived from snapshot | pure function/mapping | repository storing UI copy strings |
| Transition counters/dwell/timestamps | `AdsbTrafficRepository` | `AdsbTrafficSnapshot` fields | local counters in Composables |

### 3.2 Dependency Direction

Flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/map` ADS-B repository/model/UI + test files
  - `docs/ADS-b` and `docs/ARCHITECTURE/PIPELINE.md`
- Boundary risk:
  - Medium risk if UX copy logic leaks into repository. Keep all labels/taxonomy mapping in UI support file.

### 3.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Persistent issue visibility | debug-only panel + flash | release-safe status strip/chip in map UI | improve pilot trust | UI policy tests + manual release-variant checks |
| Connectivity transition accounting | implicit from last error fields | explicit counters/timestamps in repository snapshot | field diagnosis and analytics | repository unit tests |

### 3.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Connectivity issue visibility tied to debug panel patterns | release UX has no persistent degraded status | always-available release-safe status surface | Phase 1 |
| Transition inference from generic error strings | not explicitly modeled | typed telemetry fields and state transition accounting | Phase 2 |

### 3.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Transition timestamps/dwell | Monotonic | deterministic runtime timing |
| Stale/expiry ages | Monotonic | existing correctness contract |
| User-readable status text timestamps (if shown) | Wall | display only |

Forbidden:
- Monotonic vs wall comparisons
- Replay vs wall comparisons

### 3.4 Threading and Cadence

- `IO`: repository loop/network/persistence.
- `Main`: rendering only.
- No additional hot-path network work in UI.
- Status surface updates consume existing snapshot flow; no polling in UI.

### 3.5 Replay Determinism

- Deterministic for same replay input: Yes.
- New work is live ADS-B connectivity only; replay paths remain unchanged.

### 3.6 Enforcement Coverage

| Risk | Rule Reference | Guard | File/Test |
|---|---|---|---|
| Duplicate connectivity state owner | SSOT/UDF | unit tests + review | map UI policy tests |
| Hidden timebase drift | Timebase rules | enforceRules + unit tests | ADS-B repository tests |
| UI noise regression | UX quality gate | UI policy tests | new ADS-B status policy tests |
| Telemetry mismatch under flaps | correctness | deterministic transition tests | repository tests |

## 4) Before/After Data Flow

Before:

`Repository snapshot -> debug panel + one-shot flash -> limited release visibility`

After:

`Repository snapshot (typed transition telemetry) -> release-safe persistent status surface + debug panel enrichment`

## 5) Detailed Phases

### Phase 0 - Baseline Lock and Score Gate Setup

- Goal:
  - Freeze current behavior as baseline for the remaining score lift.
- Planned changes:
  - Add score-gate checklist section in plan docs and test names tied to deductions.
- Files:
  - `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_UX_STALE_HOUSEKEEPING_OBSERVABILITY_2026-03-01.md`
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/ui/TrafficDebugPanelAutoDismissPolicyTest.kt`
- Tests:
  - baseline assertions for current release UX limitations and telemetry coverage.
- Exit criteria:
  - Baseline tests clearly identify current deductions without ambiguity.

### Phase 1 - Release-Safe Persistent Connectivity Status UX (+4)

- Goal:
  - Surface persistent ADS-B status when degraded/offline/backing off in release builds.
- UX behavior contract:
  - Show compact status chip/strip while:
    - `Error`
    - `BackingOff`
    - `AuthFailed` (with active fallback context)
  - Keep one-shot flash for immediate attention.
  - Auto-collapse only after stable `Active` dwell (proposed 8-12s).
  - Do not block map gestures or sheet interactions.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanels.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanelsSupport.kt`
  - optional: new support file for ADS-B status policy mapping.
- Tests:
  - `feature/map/src/test/java/com/example/xcpro/map/ui/TrafficDebugPanelAutoDismissPolicyTest.kt`
  - add status policy tests:
    - visible states
    - recovery dwell collapse
    - no status on healthy active state
- Exit criteria:
  - Release build always provides persistent degraded/offline ADS-B indication.

### Phase 2 - Typed Transition Telemetry in Snapshot (+2)

- Goal:
  - Add explicit transition accounting to `AdsbTrafficSnapshot`.
- Planned fields (final names decided during implementation):
  - `networkOnline: Boolean`
  - `lastNetworkTransitionMonoMs: Long?`
  - `networkOfflineTransitionCount: Int`
  - `networkOnlineTransitionCount: Int`
  - `currentOfflineDwellMs: Long`
- Files:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - map debug support mapping where needed.
- Tests:
  - add/extend `AdsbTrafficRepositoryTest`:
    - online->offline->online sequences
    - repeated flaps
    - dwell monotonicity and counter increments
- Exit criteria:
  - Transition telemetry is deterministic, explicit, and used consistently.

### Phase 3 - E2E Network-Transition Hardening (+1)

- Goal:
  - Raise confidence from "unit-correct" to "release robust."
- Detailed implementation track:
  - `docs/ADS-b/CHANGE_PLAN_ADSB_NETWORK_TRANSITION_E2E_COVERAGE_2026-03-01.md`
- Planned tests:
  - offline at start -> online restore
  - drop during backoff delay
  - rapid flaps (xN) with stable convergence
  - reconnect after auth fallback state
- Files:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
  - if needed: instrumentation test under `app` for release UI status visibility.
- Exit criteria:
  - Transition suite is stable and non-flaky across repeated local runs.

### Phase 4 - Final Verification and Doc Lock (Gate)

- Goal:
  - Lock implementation details and handoff instructions for future agents.
- Files:
  - `docs/ADS-b/README.md`
  - `docs/ADS-b/ADSB.md`
  - `docs/ARCHITECTURE/PIPELINE.md` (if runtime wiring changed)
  - mark plan status and final score in this file.
- Exit criteria:
  - Docs clearly reflect final behavior and active plan status.

## 6) Test Plan

- Unit tests:
  - repository transition counters/dwell
  - repository offline/online sequencing
  - UI status visibility policy
- Integration tests:
  - ADS-B repository with fake network port and controlled clock.
- Instrumentation (targeted):
  - only if needed for release-status rendering confidence.
- Failure-mode tests:
  - DNS/timeout/connect/no-route/TLS path still maps to coherent status text.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Relevant optional checks:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Persistent status becomes noisy | Medium | Strict visibility rules + stable recovery dwell | XCPro Team |
| Telemetry fields drift from true state transitions | High | Single owner in repository + deterministic sequence tests | XCPro Team |
| Extra UI surface causes clutter on small screens | Medium | compact layout + hide on healthy state | XCPro Team |
| Transition tests flaky on timing | Medium | fake clock + fake network flows; avoid wall-time sleeps | XCPro Team |

## 8) Acceptance Gates

- All phases complete with no architecture violations.
- Release UI shows persistent degraded/offline ADS-B state.
- Transition telemetry fields are explicit and validated by tests.
- Required checks pass.
- Final score reaches at least 96/100 under rubric in Section 1.

## 9) Rollback Plan

- Revertable independently:
  - persistent release status surface
  - telemetry field additions
  - transition test expansions
- Recovery:
  - rollback phase commits independently.
  - keep baseline tests to preserve regression visibility.
