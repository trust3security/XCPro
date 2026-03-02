# CHANGE PLAN - ADS-B Connectivity UX + Stale Housekeeping + Observability (2026-03-01)

## 0) Metadata

- Title: ADS-B connectivity UX and resilience closure plan
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: TBD
- Status: Draft
- Plan role: Supporting workstream plan (detailed score-lift execution is tracked in `CHANGE_PLAN_ADSB_CONNECTIVITY_SCORE_LIFT_2026-03-01.md`)

Context:
- This plan addresses the remaining release-grade deductions after socket-error hardening:
  1) User-facing connectivity UX is still limited.
  2) Stale target cleanup is loop-driven and can lag during long offline waits.
  3) Runtime observability and end-to-end network-transition confidence are not strong enough.
- This plan is a follow-on to:
  - `docs/ADS-b/CHANGE_PLAN_ADSB_SOCKET_ERROR_HARDENING_2026-03-01.md` (completed baseline hardening).

Deep-pass update (2026-03-01, pass #2):
- Fixed during pass:
  1) `OpenSkyTokenRepository.invalidate()` now clears transient token-failure cooldown state,
     so credential save/clear actions can trigger immediate token retry instead of waiting up to 30s.
     - Files: `feature/map/src/main/java/com/example/xcpro/adsb/OpenSkyTokenRepository.kt`
     - Test: `feature/map/src/test/java/com/example/xcpro/adsb/OpenSkyTokenRepositoryTest.kt`
- Remaining open findings confirmed by pass:
  1) Stale/expiry progression can stall while waiting in `awaitNetworkOnline()` (loop-driven housekeeping gap).
  2) `publishFromStore(...)` path does not purge expired targets before selection.
  3) Release UX currently has one-shot issue flash but no persistent degraded-state indicator.
  4) Snapshot telemetry still lacks explicit online/offline transition dwell counters for field diagnosis.
  5) No targeted test coverage yet for stale/expiry progression during prolonged offline waits.

Deep-pass update (2026-03-01, pass #3):
- Fixed during pass:
  1) Offline-wait stale/expiry progression now advances while network is down.
     - `awaitNetworkOnline()` now performs periodic repository housekeeping ticks instead of waiting indefinitely.
  2) `publishFromStore(...)` now purges expired targets before selection.
  3) Added regression tests for both behaviors:
     - `offlineWait_progressesStaleThenExpiry_withoutAdditionalFetches()`
     - `centerUpdateWhileOffline_purgesExpiredTargetsImmediately()`
  - Files:
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
    - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
- Remaining open findings after pass #3:
  1) Release UX still lacks a persistent (non-debug) degraded/offline ADS-B indicator beyond one-shot flash.
  2) Snapshot observability and e2e network-transition coverage are still lighter than release-grade target.

Deep-pass update (2026-03-01, pass #4):
- Fixed during pass:
  1) ADS-B `BackingOff` state is now treated as an issue state by UI policy helpers.
     - Issue flash and issue-surface helpers now include `AdsbConnectionState.BackingOff`, not only `Error`.
     - Debug hide-while-connecting policy no longer hides `BackingOff`.
  2) Added UI policy regression coverage for BackingOff issue behavior.
  - Files:
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanels.kt`
    - `feature/map/src/test/java/com/example/xcpro/map/ui/TrafficDebugPanelAutoDismissPolicyTest.kt`
- Remaining open findings after pass #4:
  1) Release UX still lacks a persistent (non-debug) degraded/offline ADS-B indicator beyond one-shot flash.
  2) Snapshot observability and e2e network-transition coverage are still lighter than release-grade target.

Deep-pass update (2026-03-01, pass #5):
- Fixed during pass:
  1) Reconnect success timing now uses fresh monotonic time after offline/circuit waits.
     - Previously, success path could stamp `lastSuccessMonoMs` and target receive ages using a stale pre-wait timestamp.
     - This could skew post-reconnect freshness diagnostics and stale/expiry behavior after prolonged waits.
  2) Added regression coverage for the reconnect timestamp bug:
     - `offlineRecovery_successUsesFreshMonoTimestampAfterWait()`
  - Files:
    - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
    - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
- Remaining open findings after pass #5:
  1) Release/e2e network-transition coverage depth remains the primary deduction to close (`95 -> 96`).
     - Execution plan: `docs/ADS-b/CHANGE_PLAN_ADSB_NETWORK_TRANSITION_E2E_COVERAGE_2026-03-01.md`

## 1) Scope

- Problem statement:
  - ADS-B recovery behavior is robust internally, but user trust is reduced when connectivity degradation is not clearly surfaced in release UX.
  - Target stale/expiry behavior is currently tied to polling loop cadence and can lag when polling is paused waiting for network restoration.
  - Transition telemetry and e2e regression coverage for offline/online churn are not yet sufficient for high-confidence releases.
- Why now:
  - Remaining reliability and UX gaps are now the top blockers to a "release-grade" ADS-B connectivity slice.
- In scope:
  - Release-visible ADS-B connectivity status UX (not debug-only).
  - Decoupled stale/expiry housekeeping from fetch-loop-only timing.
  - Structured connectivity observability and stronger network-transition test coverage.
  - Documentation updates for future agent execution.
- Out of scope:
  - OpenSky API/provider contract changes.
  - OGN runtime behavior changes.
  - Large visual redesign of map controls outside ADS-B connectivity status.
- User-visible impact:
  - Clear, timely indication when ADS-B has connectivity issues.
  - Predictable stale/expiry behavior even during prolonged offline periods.
  - Fewer ambiguous "is ADS-B broken or just quiet" moments.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B live targets, failure kind, connection state | `AdsbTrafficRepository` | `snapshot` `StateFlow` | UI-owned authoritative connection mirrors |
| ADS-B release-visible status text/category | derived from `AdsbTrafficSnapshot` in UI support mapping | pure UI-derived label from SSOT | ad-hoc state machine copies in multiple UI surfaces |
| Stale/expiry timers and purge decisions | `AdsbTrafficRepository` + `AdsbTrafficStore` | internal policy + emitted `targets`/`snapshot` | parallel purge schedulers outside repository |
| Connectivity transition telemetry (dwell/counts/last transition) | `AdsbTrafficRepository` telemetry fields | `snapshot` diagnostics | untracked local counters in UI |

### 2.2 Dependency Direction

Confirmed flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/map` ADS-B repository/model/UI files.
- Boundary risk:
  - Medium if UI-specific policy leaks into repository. Mitigation: keep UI copy/text/visibility logic in UI support files only.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Stale/expiry progression timing during offline waits | fetch loop cadence only | repository-side housekeeping cadence (still repository-owned) | Keep stale/expiry behavior active when polling is paused | repository tests with forced offline dwell |
| User connectivity visibility | debug-only panel path | release-safe status surface derived from snapshot | Users need explicit issue visibility in production builds | UI tests and manual verification in release variant |
| Transition observability | partial failure fields only | explicit connectivity transition telemetry in snapshot | Improve diagnosis and release confidence | unit tests and diagnostics assertions |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| ADS-B user status shown only in debug panel | `BuildConfig.DEBUG`-gated issue messaging | release-safe ADS-B status chip/banner derived from snapshot | Phase 1 |
| Stale/expiry updates happen only when loop iterates | implicit fetch-loop timing dependency | explicit repository housekeeping tick using injected clock | Phase 2 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Target age, stale threshold, expiry threshold | Monotonic | Correct elapsed-time semantics independent of wall clock |
| Connectivity dwell/transition durations | Monotonic | Deterministic runtime diagnostics and backoff observability |
| UI one-shot issue flash visibility timeout | Monotonic/Compose effect time | UX timing only; no domain effect |
| Human-readable "last updated" labels (if added) | Wall | Display-only formatting |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - `IO`: network and persistence
  - repository internal scope for housekeeping tick and snapshot publication
  - `Main`: UI rendering only
- Primary cadence/gating sensor:
  - ADS-B polling cadence remains unchanged for provider fetches.
  - housekeeping cadence is separate and lightweight (proposed 1s tick while enabled).
- Hot-path latency budget:
  - No additional blocking work in polling fetch path.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: no new randomness proposed.
- Replay/live divergence rules:
  - ADS-B networking and connectivity UX remain live-only behavior.
  - Replay deterministic paths remain unaffected.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Release UX introduces duplicate state machine | ARCHITECTURE SSOT/UDF | UI unit tests + review | `feature/map/src/test/.../map/ui/*` |
| Housekeeping uses non-injected time calls in repository/domain path | ARCHITECTURE timebase rules | enforceRules + unit tests | ADS-B repository tests + `./gradlew enforceRules` |
| Housekeeping increases CPU churn | CODING_RULES threading/perf | unit tests + code review | repository cadence tests |
| Transition observability drifts from runtime truth | ARCHITECTURE SSOT | unit tests + review | snapshot transition tests |
| Connectivity regressions under online/offline churn | reliability requirement | e2e/integration tests | repository integration tests + instrumentation where relevant |

## 3) Data Flow (Before -> After)

Before:

`Network callback + fetch loop -> snapshot errors mostly visible in debug -> stale/expiry advances when loop iterates`

After:

`Network callback + fetch loop + housekeeping tick -> snapshot drives release-safe connectivity status UX + stale/expiry progresses independent of fetch-loop pauses`

## 4) Implementation Phases

### Phase 0 - Baseline and Failure Lock

- Goal:
  - Lock current behavior with tests for the three gap areas before refactor.
- Files to change:
  - ADS-B repository/unit test suites under `feature/map/src/test/.../adsb`.
  - map UI policy tests under `feature/map/src/test/.../map/ui`.
- Tests to add/update:
  - Confirm current stale/expiry lag during forced offline wait.
  - Confirm current release UX does not surface connectivity state (baseline expectation).
  - Confirm current transition telemetry coverage boundaries.
- Exit criteria:
  - Baseline is explicitly captured in reproducible tests.

### Phase 1 - Release-Safe Connectivity UX Surface

- Goal:
  - Provide clear user-visible ADS-B connectivity state in release builds.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanels.kt`
  - (if needed) `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanelsSupport.kt`
- Planned behavior:
  - Keep current one-shot flash indicator for attention.
  - Add a lightweight persistent status chip/banner in release-safe UI when ADS-B is degraded/offline/backing off.
  - Auto-collapse only after stable recovery dwell (proposed: connected for N seconds).
- Tests to add/update:
  - UI policy tests for visibility rules and recovery collapse behavior.
- Exit criteria:
  - User can see ADS-B issue state in release builds without opening debug tooling.

### Phase 2 - Decouple Stale/Expiry Housekeeping From Fetch Loop

- Goal:
  - Ensure stale dim and expiry purge continue correctly during prolonged offline waits.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt` (only if policy helpers needed)
- Planned behavior:
  - Add repository-owned housekeeping cadence while ADS-B streaming is enabled.
  - Run purge + reselection + snapshot publish from monotonic clock even when network wait is active.
  - Keep existing thresholds (`STALE_AFTER_SEC`, `EXPIRY_AFTER_SEC`) unless explicitly changed by policy.
- Tests to add/update:
  - Offline dwell test: dim at stale threshold and remove at expiry threshold without fetch-loop progress.
  - Stop/start lifecycle tests to ensure housekeeping does not leak jobs.
- Exit criteria:
  - Stale/expiry behavior is time-accurate during offline waits.

### Phase 3 - Connectivity Transition Observability Hardening

- Goal:
  - Improve runtime diagnosis and confidence for network churn.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
  - optional diagnostics UI surfaces where snapshot telemetry is shown
- Planned behavior:
  - Extend snapshot telemetry with explicit connectivity transition fields (for example: online flag, last transition mono, offline dwell, reconnect attempt counters).
  - Keep debug-reason mapping aligned with typed failure kind and connection state.
- Tests to add/update:
  - Transition sequence tests: online -> offline -> online, with assertions on dwell/counters and state coherence.
- Exit criteria:
  - Telemetry is sufficient to explain "what happened and for how long" in field logs/debug UI.

### Phase 4 - E2E Transition Coverage and Release Gate

- Goal:
  - Prove resilience across realistic connectivity transitions before release.
- Files to change:
  - ADS-B repository integration tests
  - map UI instrumentation tests (if release chip/banner requires Compose instrumentation)
  - docs listed below
- Tests to add/update:
  - End-to-end network transition scenarios:
    - online -> drop network -> recover
    - repeated short flaps
    - offline during backoff window
  - Assertions:
    - user-visible status transitions
    - stale/expiry correctness
    - transition telemetry correctness
- Exit criteria:
  - Required checks pass and transition tests are stable/non-flaky.

## 5) Test Plan

- Unit tests:
  - repository stale/expiry housekeeping under offline dwell
  - transition telemetry correctness
  - UI status visibility policy
- Replay/regression tests:
  - confirm replay paths unchanged (ADS-B live-only behavior isolation)
- UI/instrumentation tests (if needed):
  - release-safe connectivity status visibility and recovery collapse
- Degraded/failure-mode tests:
  - DNS/timeout/no-route/connect/TLS mappings still produce coherent user status
  - online/offline flap handling does not spam or suppress status incorrectly
- Boundary tests for removed bypasses:
  - no debug-only dependency for basic connectivity issue visibility
  - no fetch-loop-only dependency for stale/expiry progression

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Persistent status UI becomes noisy | Medium | Use concise status taxonomy + recovery dwell-based auto-collapse | XCPro Team |
| Housekeeping tick adds unnecessary churn | Medium | Keep cadence coarse/lightweight; early-return when no targets | XCPro Team |
| Additional telemetry fields drift from real transitions | High | Derive all telemetry in repository SSOT only; add transition sequence tests | XCPro Team |
| E2E tests flaky due timing races | Medium | Use fake clocks/fake network ports for deterministic tests; keep instrumentation minimal | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- User-visible ADS-B connectivity issue state is present in release UI.
- Stale/expiry progression is correct during prolonged offline waits.
- Transition observability fields are explicit and test-covered.
- `KNOWN_DEVIATIONS.md` remains unchanged unless explicitly approved.

## 8) Rollback Plan

- What can be reverted independently:
  - release status UX surface
  - repository housekeeping cadence
  - transition telemetry extensions
- Recovery steps:
  - revert phase-specific commits independently if regressions appear.
  - keep baseline tests to preserve visibility into reopened gaps.
