# RacingNavigationEngine test-suite split plan

## 0) Metadata

- Title: `RacingNavigationEngineTest` suite split
- Owner: Codex
- Date: 2026-03-13
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  - `feature/tasks/src/test/java/com/example/xcpro/tasks/racing/navigation/RacingNavigationEngineTest.kt`
    is a large mixed-concern regression suite.
  - Start behavior, finish behavior, altitude/close-time policy, and basic
    course progression currently fail in one file, which slows diagnosis.
- Why now:
  - this is a critical task-domain regression suite
  - its concerns are already separable by failure domain without touching
    production behavior
- In scope:
  - test-only refactor
  - shared fixture extraction
  - behavior-based split into focused suites
- Out of scope:
  - no production code changes
  - no racing-navigation algorithm changes
  - no replay or boundary-planner refactor

## 2) Architecture Contract

### 2.1 SSOT Ownership

No production SSOT owners change. This refactor is limited to test sources.

### 2.2 Dependency Direction

Production dependency flow remains unchanged:

`UI -> domain -> data`

### 2.3 Time Base

Existing task-navigation time semantics must remain explicit in tests:

| Value | Time Base | Why |
|---|---|---|
| `timestampMillis` on fixes | navigation/replay event timeline | crossing interpolation and policy windows |
| gate/close/landing windows | same event timeline | deterministic state transitions |

Forbidden:

- changing timestamp semantics while moving tests
- replacing explicit fix windows with opaque helpers that hide timing intent

### 2.4 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules: unchanged

## 3) Target Suite Ownership

- `RacingNavigationEngineTest.kt`
  - core start/progression behavior and representative baseline transitions
- `RacingNavigationEngineStartPolicyTest.kt`
  - gate-open, wrong-direction, PEV, altitude, and candidate-selection policy
- `RacingNavigationEngineFinishRulesTest.kt`
  - finish direction, close-time, min-altitude, landing-without-delay, and
    stop-plus-five finish policy
- keep unchanged:
  - `RacingNavigationEnginePhase4Test.kt`
  - `RacingNavigationEngineSupportTest.kt`

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  - map every current test to a destination file
  - preserve the existing `RacingNavigationEngineTest` class as a useful core
    suite to reduce stale command churn

### Phase 1 - Shared fixture extraction

- Goal:
  - move reused task builders into a dedicated test fixture file
- Exit criteria:
  - split suites share one source of truth for common task shapes

### Phase 2 - Concern-based split

- Goal:
  - move tests into `core`, `start policy`, and `finish rules` suites without
    changing assertions
- Exit criteria:
  - each resulting file stays comfortably reviewable

### Phase 3 - Verification

- Goal:
  - verify the racing-navigation slice still compiles and runs
- Required checks:
  - focused `feature:tasks` test execution for the racing-navigation suites
  - repo-level `enforceRules`
  - repo-level `testDebugUnitTest`
  - repo-level `assembleDebug`

## 5) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| stale docs/commands still point at `RacingNavigationEngineTest` | Medium | keep the base class name for the core suite | Codex |
| split creates overlap with phase-4/support suites | Medium | preserve explicit ownership boundaries | Codex |
| helper extraction hides scenario meaning | Medium | keep helpers limited to task builders only | Codex |
| assertion drift during file moves | High | move tests verbatim first, then do only minimal cleanup | Codex |

## 6) Acceptance Gates

- no production files change
- no navigation behavior assertions change
- suite ownership is clearer than the original monolith
- focused racing-navigation verification passes, or unrelated blockers are
  reported explicitly
