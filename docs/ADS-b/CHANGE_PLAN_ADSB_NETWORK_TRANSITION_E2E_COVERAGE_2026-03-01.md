# CHANGE PLAN - ADS-B Release/E2E Network-Transition Coverage (2026-03-01)

## 0) Metadata

- Title: ADS-B network-transition release/e2e coverage hardening
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: TBD
- Status: In Progress (Phase 3 implemented, stability replay in progress)
- Linked parent plan:
  - `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_SCORE_LIFT_2026-03-01.md`
- Current score context:
  - Working score is `95/100`
  - This plan is the execution path for the final `+1` to reach `96/100`

Implementation progress (2026-03-01):
- Completed in this pass:
  1) Repository transition matrix expanded with additional churn scenarios:
     - `networkDropDuringCircuitOpenWait_pausesProbeUntilReconnect()`
     - `authFailedMode_survivesOfflineRecovery_withoutBlockingPolling()`
     - `extendedOfflineOnlineFlapping_countsTransitionsAndConverges()`
  2) Release-status UI rendering coverage added (Robolectric Compose):
     - `AdsbStatusBadgesUiTest` validates persistent status and issue flash rendering for degraded states.
     - persistent issue dwell policy extracted to shared reducer (`reducePersistentIssueVisibility`) used by composable + deterministic policy unit tests.
  3) Feature-module instrumentation coverage scaffold added:
     - `feature/map/src/androidTest/.../AdsbStatusBadgesInstrumentedTest.kt`
     - instrumentation test APK compile verified via `:feature:map:assembleDebugAndroidTest`
     - instrumentation discovery fixed by setting `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `feature/map/build.gradle.kts`.
  4) Release/e2e transition instrumentation depth implemented (`UI-01..UI-04`):
     - offline-at-start persistent + flash visibility
     - backoff-state persistent + flash visibility
     - recovery-dwell auto-dismiss
     - issue-return-during-dwell flap resilience (no stuck state)
     - shared timed-visibility policy extracted (`rememberTimedVisibility`) for reuse/testability
     - badge test tags added for robust instrumentation assertions
  4) Verification commands executed successfully for this scope:
     - `./gradlew.bat :feature:map:testDebugUnitTest --tests ...AdsbTrafficRepositoryTest... --tests ...AdsbStatusBadgesUiTest --tests ...PersistentIssueVisibilityPolicyTest --tests ...TrafficDebugPanelAutoDismissPolicyTest`
     - `./gradlew.bat :feature:map:assembleDebugAndroidTest`
     - `./gradlew.bat enforceRules`
     - `./gradlew.bat testDebugUnitTest`
     - `./gradlew.bat assembleDebug`
     - `./gradlew.bat --no-configuration-cache :feature:map:connectedDebugAndroidTest --no-parallel` (7 tests, 0 failed on attached device)
     - `./gradlew.bat --no-configuration-cache :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` (9 tests, 0 failed)
     - `./gradlew.bat --no-configuration-cache connectedDebugAndroidTest --no-parallel` (multi-module instrumentation green)
- Remaining for final gate:
  1) Repeated instrumentation stability evidence (Phase 4 thresholds).

Implementation update (2026-03-28):
- Fixed a field-reported recovery bug where ADS-B could stay red until process
  restart after connectivity had already returned.
- Root cause: the runtime offline/retry wait path trusted the callback-backed
  `isOnline` flow only; if that flow stayed stale-false after recovery, ADS-B
  never resumed polling.
- Fix:
  - `AdsbNetworkAvailabilityPort` now exposes `currentOnlineState()`.
  - the Android adapter provides a fresh `ConnectivityManager` snapshot.
  - ADS-B wait/retry paths now re-check that fresh snapshot before remaining
    offline.
- Regression added:
  - `staleOfflineFlow_recoversFromFreshNetworkSnapshotWithoutRestart()`
- Detailed note:
  - `docs/ADS-b/CHANGE_NOTE_ADSB_STALE_OFFLINE_RECOVERY_2026-03-28.md`

## 1) Scope

- Problem statement:
  - ADS-B runtime behavior is strong in unit/integration tests, but release-grade confidence is still reduced by limited end-to-end coverage of online/offline transitions and user-visible status transitions.
- Why now:
  - This is the only primary remaining deduction in the ADS-B score model.
- In scope:
  - Deterministic transition scenario matrix across repository + UI + instrumentation.
  - Release-visible UI transition assertions for offline/recovery states.
  - Test harness seams for deterministic network and provider behavior in androidTest.
  - CI-ready flake controls and release gate checklist.
- Out of scope:
  - OpenSky API/provider contract changes.
  - OGN behavior changes.
  - Broad map UI redesign.
- User-visible impact:
  - Higher confidence that pilots consistently see correct ADS-B issue/recovery states under real connectivity churn.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B connection state and transition telemetry | `AdsbTrafficRepository` | `AdsbTrafficSnapshot` | UI-local connection state machines |
| ADS-B release status rendering rules | map UI policy functions | pure mapping from snapshot | repository-owned UI copy |
| Network-transition test control state | test harness fakes | test-only API/flows | global mutable production singletons |

### 2.2 Dependency Direction

Flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched (planned):
  - `feature/map` ADS-B repository/UI policy tests
  - `app/src/androidTest` instrumentation coverage
  - optional debug/test DI seam files if needed for deterministic injection
- Boundary risk:
  - Medium if test seams leak into production runtime behavior.
  - Mitigation: confine deterministic test controls to test/debug sources or explicit no-op default seams.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Network transition orchestration in instrumentation | implicit device/network behavior | deterministic test fake network port | remove non-deterministic device dependency | stable androidTest scenario runs |
| UI transition observability in tests | inferred from logs/text only | explicit UI semantic tags + assertions | robust release UI verification | Compose/UI instrumentation assertions |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Transition verification by manual ad-hoc testing | non-repeatable pilot/manual checks | deterministic scenario matrix in unit + instrumentation | Phase 2/3 |
| Timing assertions with wall-time sleeps | flaky delay-driven checks | injected monotonic clock + bounded idling/waits | Phase 1/3 |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Transition counters and dwell assertions | Monotonic | deterministic offline/online verification |
| Badge auto-dismiss dwell assertions | Monotonic | remove wall-time jitter from tests |
| Human-readable timestamps (if surfaced) | Wall | display-only |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Repository work remains on `IO`.
- UI rendering/assertions remain on `Main`.
- Instrumentation harness transitions are event-driven (no long blocking sleeps).
- Test waits use bounded polling/idling helpers only.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No new randomness introduced.
- Replay/live divergence:
  - Coverage work targets live ADS-B path only.
  - Replay behavior remains unchanged.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Test seam leakage into production | Architecture boundary rules | review + enforceRules + source-set confinement | app debug/androidTest seam files |
| Non-deterministic timing in e2e tests | Timebase/cadence rules | unit/integration/androidTest with fake clocks or bounded idling | ADS-B transition tests |
| Missing release-visible UX assertion | UX reliability requirement | instrumentation assertions on status badge/flash | new ADS-B androidTests |
| Transition telemetry drift | SSOT correctness | repository transition matrix assertions | `AdsbTrafficRepositoryTest` |

## 3) Coverage Gap Decomposition

1. Release UI transition assertions are not yet fully instrumented.
2. End-to-end transition matrix is only partially covered in unit tests.
3. No explicit stability/flakiness budget for transition churn tests.

Target state for closure:

- Deterministic scenario matrix covering startup offline, drop during active polling, drop during backoff, rapid flapping, and auth-fallback recovery.
- Release UI verified for:
  - issue flash on degrade
  - persistent status visibility while degraded
  - delayed collapse after stable recovery
- Stable test pass criteria across repeated local/CI runs.

## 4) Before -> After Flow

Before:

`Repository transition logic tested mostly at unit level -> limited instrumentation of release UI transitions`

After:

`Repository transition matrix + release instrumentation harness -> deterministic end-to-end transition assertions -> release gate confidence`

## 5) Detailed Phases

### Phase 0 - Baseline Lock and Scenario Contract

- Goal:
  - Freeze the exact transition scenarios and expected outputs before new tests.
- Planned files:
  - `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_SCORE_LIFT_2026-03-01.md`
  - this plan file
- Work:
  - Define scenario IDs and expected snapshot/UI outcomes.
  - Define pass criteria and flaky thresholds.
- Exit criteria:
  - Scenario contract is unambiguous and approved.

### Phase 1 - Deterministic Testability Seams

- Goal:
  - Ensure instrumentation can control network/provider deterministically.
- Planned files:
  - `feature/map/src/main/java/com/example/xcpro/di/MapBindingsModule.kt` (only if binding seam is needed)
  - `app/src/debug/...` and/or `app/src/androidTest/...` seam/test fake files
  - optional gradle/test dependencies for instrumentation DI overrides
- Work:
  - Provide test-only override path for:
    - `AdsbNetworkAvailabilityPort`
    - `AdsbProviderClient`
    - clock/time source where needed
  - Add robust UI semantics tags for ADS-B status surfaces if not already present.
- Exit criteria:
  - androidTest can force online/offline/provider outcomes without device network toggling.

### Phase 2 - Repository Transition Matrix Expansion

- Goal:
  - Complete deterministic transition matrix at unit/integration level.
- Planned files:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
  - optional helper fakes under `feature/map/src/test/.../adsb/support`
- Scenario matrix to cover:
  - `NT-01` startup offline -> online recovery
  - `NT-02` network drop during delay window
  - `NT-03` repeated failures -> circuit open -> probe -> recover
  - `NT-04` network drop during backoff/circuit-open wait
  - `NT-05` rapid flap sequence (>=5 transitions) with convergent active state
  - `NT-06` auth-fallback context + network recovery path
- Assertions required:
  - connection state transitions
  - transition counters/timestamps/dwell
  - no duplicate loop start or stalled retries
- Exit criteria:
  - Matrix tests pass deterministically with zero flaky retries in repeated local runs.

### Phase 3 - Release UI Transition Instrumentation

- Goal:
  - Validate release-visible ADS-B issue/recovery UX end-to-end.
- Planned files:
  - `app/src/androidTest/java/com/example/xcpro/adsb/AdsbNetworkTransitionInstrumentedTest.kt` (new)
  - `app/src/androidTest/java/com/example/xcpro/adsb/support/...` (new helper fakes/harness)
  - UI files only if semantic hooks are missing:
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapAdsbPersistentStatus.kt`
    - `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanels.kt`
- Instrumented scenarios:
  - `UI-01` offline-at-start shows persistent degraded status quickly.
  - `UI-02` reconnect transitions to healthy then auto-dismisses after configured dwell.
  - `UI-03` backoff/circuit-open state displays issue state without map interaction regression.
  - `UI-04` rapid flap preserves coherent user signal (no stuck hidden/stuck visible state).
- Assertions required:
  - issue flash visible during degraded states.
  - persistent status visible for degraded states.
  - persistent status hidden only after healthy dwell.
  - no crash or deadlock during transitions.
- Exit criteria:
  - Instrumentation verifies release UX transitions for all required scenarios.

### Phase 4 - Stability Hardening and CI Gate

- Goal:
  - Convert new tests from local proof to CI/release gate quality.
- Planned files:
  - test helper files and any CI scripts/config touched by test execution strategy
  - docs updates in `docs/ADS-b` and optionally `docs/ARCHITECTURE/PIPELINE.md` if wiring changed
- Work:
  - Run transition suites repeatedly and remove flaky waits.
  - Cap per-scenario runtime and remove slow polling where possible.
  - Add/adjust CI invocation guidance for transition instrumentation path.
- Stability thresholds:
  - Unit/integration transition matrix: 10/10 repeated local passes.
  - Instrumentation transition suite: 5/5 repeated local passes on one device/emulator target.
- Exit criteria:
  - Transition coverage is stable enough for release gating.

### Phase 5 - Final Score Gate and Doc Lock

- Goal:
  - Mark Phase 3 deduction closed and lock the operational contract.
- Planned files:
  - `docs/ADS-b/CHANGE_PLAN_ADSB_CONNECTIVITY_SCORE_LIFT_2026-03-01.md`
  - `docs/ADS-b/ADSB.md`
  - `docs/ADS-b/README.md`
- Work:
  - Record completed scenario IDs and evidence commands.
  - Update score from `95/100` to `96/100` only after gates pass.
- Exit criteria:
  - All gates pass and docs clearly indicate closure state.

## 6) Scenario Matrix (Canonical)

| ID | Start State | Transition Script | Required Assertions |
|---|---|---|---|
| NT-01 | Enabled + offline | offline -> online | `networkOfflineTransitionCount=1`, then online count increments, active recovers |
| NT-02 | Active + waiting for next poll | online -> offline during wait -> online | timer interrupt path works; no extra request churn; active recovers |
| NT-03 | repeated transient failures | fail x3 -> circuit open -> probe success | circuit state transitions coherent; retry timestamps sane |
| NT-04 | in backoff/circuit-open wait | offline during backoff -> online | waits on network, no burn of stale retry timer budget |
| NT-05 | Active | flap online/offline N times | counters monotonic and final state converges |
| NT-06 | Auth fallback context | auth-failed/anonymous fallback + network drop/recover | user status coherent, recovery path not blocked |
| UI-01 | Map visible + offline | launch offline | persistent issue status appears |
| UI-02 | Issue visible | recover online stable | persistent issue auto-dismisses only after healthy dwell |
| UI-03 | Backoff/circuit-open | remain degraded for interval | issue flash + persistent status remain coherent |
| UI-04 | Rapid flap on map | online/offline quick toggles | no stuck UI state, no crash, no interaction regression |

## 7) Test Plan

- Unit tests:
  - Extend repository matrix coverage in `AdsbTrafficRepositoryTest`.
- Integration tests:
  - Continue fake network/provider sequencing with injected clock.
- UI/instrumentation tests:
  - Add ADS-B transition suite under `app/src/androidTest`.
- Degraded/failure-mode tests:
  - DNS/timeout/connect/no-route/TLS + offline callback transitions.
- Boundary tests for removed bypasses:
  - no device-level network toggling required; deterministic fake-driven transitions only.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 8) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Instrumentation flakes from timing variance | High | deterministic fake transitions + bounded wait helpers + repeated run gate | XCPro Team |
| Test-only seam leaks into production behavior | High | source-set confinement and explicit no-op defaults | XCPro Team |
| UI assertions brittle to copy/layout text changes | Medium | semantic tags and state-driven assertions | XCPro Team |
| CI runtime growth | Medium | scenario batching and focus suite for transition gate | XCPro Team |

## 9) Acceptance Gates

- All scenario IDs (`NT-01..NT-06`, `UI-01..UI-04`) have passing automated coverage.
- New tests meet stability thresholds in Phase 4.
- No architecture/rules drift introduced.
- Release/e2e network-transition deduction is closed in score-lift plan.
- ADS-B score can be upgraded from `95/100` to `96/100`.

## 10) Rollback Plan

- Revertable independently:
  - instrumentation harness additions
  - repository matrix tests
  - UI semantic test hooks
- Recovery:
  - keep unit matrix assertions even if instrumentation layer is temporarily rolled back
  - preserve this plan file as canonical re-entry checklist
