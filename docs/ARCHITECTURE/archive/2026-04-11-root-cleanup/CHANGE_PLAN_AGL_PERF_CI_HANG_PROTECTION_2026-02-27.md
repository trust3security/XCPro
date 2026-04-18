# CHANGE_PLAN_AGL_PERF_CI_HANG_PROTECTION_2026-02-27.md

## 0) Metadata

- Title: AGL Perf Evidence + CI Hang Protection + Rollback Note
- Owner: XCPro map/sensors + build/test infra
- Date: 2026-02-27
- Issue/PR: TBD
- Status: Implemented (local), release-gate verification pending

## 1) Scope

- Problem statement:
  - AGL worker behavior is functionally hardened, but release evidence for performance and CI test-hang resilience is incomplete.
  - Current unit-test flow can fail or stall due Robolectric/runtime flakiness and Kotlin/Gradle cache edge cases.
  - Rollback instructions are not consistently captured in plan/PR notes for fast recovery.
- Why now:
  - This path is hot (sensor cadence) and release-critical.
  - CI stability and fast rollback are required for release-grade confidence.
- In scope:
  - `#7` Perf evidence for AGL burst path (before/after timing and allocation evidence).
  - `#9` CI hang protection: per-test timeout policy + retry policy for flaky Robolectric classes.
  - `#10` Rollback note: one-step revert instructions in change plan and PR notes.
  - Two-tier verification model: fast local minimum gate for daily iteration + full release gate before merge.
- Out of scope:
  - New feature behavior in flight metrics beyond test/infra/perf evidence.
  - Replay pipeline model changes.
- User-visible impact:
  - None directly; improved stability and safer release operations.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| AGL perf run evidence | `docs/` evidence artifacts | Markdown/CSV summaries | Ad-hoc local-only evidence not committed for release |
| Flaky retry allowlist | Build/test infra config | Gradle/test config + allowlist file | Hidden class lists inside scripts only |
| Rollback procedure | Change plan + PR notes | Explicit command + validation steps | Undocumented rollback in chat-only notes |

### 2.2 Dependency Direction

Confirmed unchanged:

`UI -> domain -> data`

- Modules/files touched (planned):
  - `feature/map` tests (perf evidence and timeout annotations/rules).
  - Root/module `build.gradle.kts` (retry/timeout policy wiring).
  - `docs/ARCHITECTURE/*` and PR notes.
- Boundary risk:
  - Low for app logic; medium for CI behavior if retry/timeout policy is too aggressive.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Test hang detection policy | Ad-hoc per test | Shared test infra policy | Remove silent hangs/timeouts | CI run + targeted flaky class tests |
| Flaky retry targeting | None/manual rerun | Explicit allowlist in build config | Deterministic retries, reviewable scope | Retry report + allowlist review |
| Rollback instructions | Tribal knowledge | Plan + PR template notes | One-step operational safety | PR checklist gate |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Manual local reruns for flaky Robolectric hangs | Developer reruns without policy | CI retry policy with allowlist + timeout guard | Phase 2/3 |
| Missing rollback command in PR | Chat/manual instructions | Mandatory rollback section in plan/PR notes | Phase 4 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| AGL perf elapsed timing | Monotonic | Stable runtime measurement |
| Evidence file timestamps | Wall | Traceability for runs |
| Replay determinism checks | Replay | Preserve replay invariants unchanged |

Forbidden comparisons remain unchanged:
- Monotonic vs wall in domain logic
- Replay vs wall in domain logic

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Perf evidence tests run on deterministic test dispatchers plus dedicated worker dispatchers where needed.
- Primary cadence/gating sensor:
  - AGL update burst path (`updateAGL`) in sensor processing.
- Hot-path latency budget:
  - Maintain non-blocking update path; no regression versus baseline evidence.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (no replay logic change planned).
- Randomness used: No.
- Replay/live divergence rules: unchanged.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| AGL hot-path perf regression not evidenced | CONTRIBUTING DoD perf budget | perf evidence test + committed report | `feature/map` perf test + docs artifact |
| Unit tests hanging indefinitely | CONTRIBUTING test expectations | per-test timeout + CI gate | test infra rule + Gradle config |
| Flaky retries masking real failures | CI quality discipline | allowlist + retry caps + report review | Gradle retry config + docs |
| Slow rollback during incident | Release readiness | rollback note gate in plan/PR | change plan + PR checklist |

## 3) Data Flow (Before -> After)

Before:

`AGL changes -> tests run -> pass/fail (limited perf evidence, ad-hoc flaky handling, rollback implicit)`

After:

`AGL changes -> perf evidence run (before/after artifacts) -> CI timeout/retry policy -> deterministic reports -> explicit rollback command in plan/PR`

## 4) Implementation Phases

### Phase 0 - Baseline and Criteria
- Goal:
  - Define measurable perf and CI-hang acceptance criteria before code/config changes.
- Files to change:
  - This plan file only.
- Tests to add/update:
  - None.
- Exit criteria:
  - Baseline commands and pass/fail thresholds defined:
    - Perf regression budget for AGL burst path:
      - `p50` elapsed regression <= `+15%` vs baseline.
      - `p95` elapsed regression <= `+20%` vs baseline.
      - allocation regression (bytes per burst) <= `+10%` vs baseline.
    - Timeout policy defaults:
      - JVM unit-test method timeout: `60s` default.
      - Optional per-class override: up to `120s` with rationale.
    - Retry policy defaults:
      - CI-only retry for allowlisted flaky Robolectric classes.
      - Max retries: `1` (single retry only).

### Phase 1 - Perf Evidence Harness (`#7`)
- Goal:
  - Capture before/after timing and allocation evidence for AGL burst path.
- Files to change:
  - `feature/map/src/test/...` (AGL perf evidence test/harness).
  - `docs/ARCHITECTURE/evidence/...` (committed evidence summary, run metadata).
  - Optional helper script under `scripts/`.
- Tests to add/update:
  - AGL perf evidence test with clear pass criteria:
    - Burst scenario: `5000` rapid `updateAGL` submissions.
    - Evidence metrics: `p50`, `p95`, total elapsed, allocation bytes per burst.
    - Compare against baseline with Phase 0 regression budgets.
- Exit criteria:
  - Evidence artifacts committed and reproducible command documented.

### Phase 2 - Per-Test Timeout Policy (`#9`, part A)
- Goal:
  - Add explicit timeout policy so hanging tests fail fast and visibly.
- Files to change:
  - Shared test rule or selected Robolectric classes (including known flaky classes).
  - Gradle unit-test task config if needed for global timeout defaults.
- Tests to add/update:
  - Validate timeout behavior with a synthetic hanging test (or controlled long-running test).
- Exit criteria:
  - Hanging behavior converts to bounded failure with actionable error.
  - Default timeout (`60s`) and override rule (`<=120s`, rationale required) are documented in test infra.

### Phase 3 - Retry Policy for Flaky Robolectric Classes (`#9`, part B)
- Goal:
  - Add controlled retry policy for reviewed flaky classes only.
- Files to change:
  - Build config (`build.gradle.kts`) and allowlist file:
    - `config/test/flaky-robolectric-allowlist.txt`
  - CI config/job definition if pipeline-level retry wiring is needed.
- Tests to add/update:
  - Verify retries trigger only for allowlisted classes and do not hide persistent failures.
- Exit criteria:
  - Retry policy active, bounded, and reportable.
  - Only allowlisted classes can retry; persistent failures still fail build.

### Phase 4 - Rollback Note and PR Hygiene (`#10`)
- Goal:
  - Make rollback one-step and mandatory in plan/PR notes.
- Files to change:
  - This change plan (Rollback section complete).
  - PR notes/template usage instructions.
- Tests to add/update:
  - N/A (process gate).
- Exit criteria:
  - Rollback command + validation steps present and reviewed.

## 5) Test Plan

- Unit tests:
  - AGL perf evidence test/harness.
  - Timeout behavior test for hanging pattern.
  - Retry policy verification for allowlisted flaky classes.
- Replay/regression tests:
  - No replay behavior change expected; run relevant replay regression when touching shared test infra if needed.
- UI/instrumentation tests (if needed):
  - None required for this scope.
- Degraded/failure-mode tests:
  - Simulated AGL burst + timeout + flaky retry scenarios.
- Boundary tests for removed bypasses:
  - Ensure manual rerun is no longer required to identify hangs.

### 5.1 Fast Minimum Local Gate (for daily iteration)

Goal:
- Keep build/test loop short while preserving signal for this change scope.

Commands:

```bash
./gradlew enforceRules
test-safe.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.sensors.FlightCalculationHelpersTest"
./gradlew :feature:map:compileDebugKotlin
```

Notes:
- No `clean` in fast loop.
- Use targeted class filters while implementing each phase.
- Add phase-specific targeted tests as they are introduced (perf/timeout/retry tests).

### 5.2 Full Release Gate (required before merge)

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Targeted checks (planned):

```bash
./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.sensors.FlightCalculationHelpersTest"
./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.MapScreenViewModelTest.adsbCenter_updatesFromOwnshipGpsLocation" --tests "com.trust3.xcpro.map.MapScreenViewModelTest.adsbOwnshipReference_clearsWhenGpsBecomesUnavailable"
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Perf evidence noisy across machines | Medium | Run controlled repeats and report median/p95 + environment metadata | map/sensors |
| Timeout threshold too strict | Medium | Start conservative, tune by observed p95, limit scope to Robolectric classes first | build/test infra |
| Retry policy hides real defects | High | Allowlist-only retries, low max retries, keep failure if persists | build/test infra |
| Tooling cache corruption (Kotlin incremental) obscures signal | Medium | Add clean fallback path and document recovery command sequence | build/test infra |

## 7) Acceptance Gates

- Fast minimum local gate passes during implementation (Section 5.1).
- Full release gate passes before merge (Section 5.2), or blocker is documented with owner/expiry.
- Perf evidence committed with before/after metrics and run metadata.
- Per-test timeout policy active for targeted flaky Robolectric classes.
- Retry policy is bounded, allowlisted, and produces reviewable output.
- Rollback section contains one-step revert command and post-revert checks.

## 8) Rollback Plan (`#10`)

- What can be reverted independently:
  - CI timeout/retry config changes can be reverted without touching runtime AGL logic.
  - Perf evidence harness/tests can be reverted independently from production code.
- One-step rollback command (post-merge):
  - `git revert <merge_or_commit_sha>`
- Recovery verification after rollback:
  1. `./gradlew enforceRules`
  2. `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.sensors.FlightCalculationHelpersTest"`
  3. `./gradlew assembleDebug`
- PR note requirement:
  - Include the exact rollback SHA placeholder and the three validation commands above.

## 9) Implementation Snapshot (2026-02-27)

- `#7 Perf evidence`:
  - Added opt-in perf evidence test:
    - `feature/map/src/test/java/com/trust3/xcpro/sensors/FlightCalculationHelpersPerfEvidenceTest.kt`
  - Added committed evidence doc scaffold with reproducible command:
    - `docs/ARCHITECTURE/evidence/AGL_BURST_PERF_EVIDENCE_2026-02-27.md`
- `#9 CI hang protection`:
  - Added JVM test timeout policy wiring and CI-only retry policy with allowlist:
    - `build.gradle.kts`
    - `config/test/flaky-robolectric-allowlist.txt`
  - Added explicit per-test timeout rule utility for non-Robolectric JVM tests:
    - `feature/map/src/test/java/com/trust3/xcpro/testing/TestTimeoutPolicy.kt`
  - Kept `MapScreenViewModelTest` on CI retry allowlist instead of class-level JUnit timeout,
    because Robolectric main-looper idling requires main-thread execution.
- `#10 Rollback note`:
  - Added mandatory rollback section to PR template:
    - `.github/pull_request_template.md`
