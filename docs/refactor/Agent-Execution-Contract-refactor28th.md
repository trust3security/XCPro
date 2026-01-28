# AGENT.md - Autonomous Engineering Execution Template

Use this file as the single source of truth when asking an agent (Codex) to
implement a change end-to-end. This version is filled in for the
FlightDataViewModel refactor so the agent can proceed without assistance.

---

# 0) Agent Execution Contract (READ FIRST)

This document is the authoritative specification for this work.

The executing agent (Codex) is responsible for implementing the requested change
from start to finish as a self-directed software engineering task.

## 0.1 Authority
- Proceed through all phases without asking for confirmation.
- Do not ask questions unless blocked by genuinely missing information that
  cannot be inferred from the repository.
- If ambiguity exists, choose the most reasonable repo-consistent option and
  document the assumption in commits and/or PR notes.

## 0.2 Responsibilities
- Implement the change described in Section 1 (Change Request).
- Run builds, unit tests, and lint locally.
- Fix all build/test/lint failures encountered.
- Preserve existing user-visible behavior unless explicitly stated otherwise.
- Keep business logic pure and unit-testable.
- Prefer deterministic, injectable time sources:
  - Use monotonic time for staleness/elapsed logic.
  - Use wall time only for display timestamps.
  - Do not mix time sources in a single decision path.

## 0.3 Workflow Rules
- Work phase-by-phase, in order.
- Commit after each phase with a clear, scoped message.
- Do not leave TODOs or partial implementations in production paths.
- If an existing test must change, justify it strictly as behavior parity or
  updated requirements (cite Section 1).

## 0.4 Definition of Done
Work is complete only when:
- All phases in Section 2 (Execution Plan) are implemented.
- Tests cover the behavior checklist in Section 3 (Acceptance Criteria).
- Required commands in Section 4 (Required Checks) pass.
- The change is documented in Section 5 (Notes / ADR) if new decisions were made.

---

# 1) Change Request (FILLED)

## 1.1 Feature Summary (1-3 sentences)
- Refactor `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt`
  to stay under 400 LOC without changing behavior or public API.
- Extract cohesive helper logic into new internal classes/files while preserving
  MVVM/UDF/SSOT rules and existing state/flow semantics.

## 1.2 User Stories / Use Cases
- As a developer, I want the ViewModel to be readable and <400 LOC, so that
  refactors are safe and reviews are easier.
- As a maintainer, I want behavior unchanged, so that users see identical UI
  and tests remain valid.

## 1.3 Non-Goals (explicitly out of scope)
- No functional behavior changes (UI state, flows, or outputs).
- No UI changes, no feature changes, no new dependencies.
- No architectural rule changes; only refactor.

## 1.4 Constraints
- Platforms / modules:
  - Android app module(s): none
  - Libraries / layers affected: `dfcards-library` only
- Performance / battery:
  - No regressions; do not add new allocations in hot paths.
- Backwards compatibility:
  - Preserve existing state keys, flow outputs, and serialization.
- Safety / compliance:
  - Must comply with ARCHITECTURE.md, CODING_RULES.md, CONTRIBUTING.md, README.md.
  - No UI types in ViewModel; no SharedPreferences access; lifecycle-aware flows only.

## 1.5 Inputs / Outputs
- Inputs:
  - Existing repositories/flows injected into FlightDataViewModel.
- Outputs:
  - UI changes: none.
  - Data stored: unchanged.
  - Logs / metrics: unchanged.

## 1.6 Behavior Parity Checklist
- All public StateFlows/Flows emit identical values for identical inputs.
- Event/intent handling results in identical UI state transitions.
- Derived/calculated UI models are unchanged.
- Threading and dispatcher usage unchanged.
- No change to visibility or public API surface.

---

# 2) Execution Plan (FILLED)

## Phase 0 - Baseline & Safety Net
- Read `FlightDataViewModel.kt` and identify cohesive sections (state init,
  flow builders, event handlers, mappers).
- Confirm existing tests (if any) that exercise ViewModel outputs.
- Note any implicit contracts in comments or adjacent classes.

Gate: no functional changes; repo builds.

## Phase 1 - Core Implementation
- Extract helpers into new internal files in the same package:
  - `FlightDataStateMapper.kt`
  - `FlightDataFlowBuilder.kt`
  - `FlightDataUiEventHandler.kt`
  - `FlightDataProfileCoordinator.kt`
  - `FlightDataTemplateManager.kt`
- Keep ViewModel wiring only (dependency injection + delegation).
- Add or adjust unit tests if there is no coverage for extracted logic.

Gate: unit tests pass; behavior matches Section 3.

## Phase 2 - Integration & Wiring
- Ensure extracted helpers are wired with identical dependencies.
- No UI changes; only internal wiring updates.
- Add minimal VM test if behavior coverage is missing.

Gate: feature works end-to-end in debug build.

## Phase 3 - Hardening
- Validate no new allocations in hot flows; keep existing dispatchers.
- Remove any dead code uncovered by the refactor.

Gate: all required checks pass (Section 4).

## Phase 4 - Polish & PR Readiness
- Ensure `FlightDataViewModel.kt` < 400 LOC.
- Keep diffs minimal; only structural extraction.
- Provide PR summary and tests run.

Gate: Definition of Done satisfied.

---

# 3) Acceptance Criteria (FILLED)

## 3.1 Functional Acceptance Criteria
- Given identical inputs, ViewModel emits identical UI state before/after refactor.
- ViewModel file size is under 400 lines.

## 3.2 Edge Cases
- Empty/initial state behaves the same as before.
- Error states and retry flows (if present) unchanged.

## 3.3 Test Coverage Required
- Unit tests for extracted logic (or existing VM tests must continue to pass).
- At least one VM test covering a key state/flow path if missing.

---

# 4) Required Checks (AGENT MUST RUN AND PASS)

Minimum:
- `.\gradlew.bat enforceRules`
- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat lintDebug`
- `.\gradlew.bat assembleDebug`

Optional (if repo has them):
- `./gradlew detekt`
- `./gradlew ktlintCheck`
- `./gradlew connectedDebugAndroidTest`

Agent must report:
- Commands run
- Results (pass/fail)
- Any fixes applied

---

# 5) Notes / ADR (Architecture Decisions Record)

If any non-trivial decision is made, record it here:
- Decision: Wrap Log.d calls in FlightDataViewModel with a logDebug helper that swallows JVM "not mocked" exceptions.
- Alternatives considered: Enable testOptions.returnDefaultValues; remove Log.d statements.
- Why chosen: Keeps production logging intact while avoiding JVM unit test failures without build config changes.
- Impact / risks: Logs are skipped only when Log.d throws (JVM tests); no runtime behavior change expected.
- Follow-ups: None.

---

# 6) Agent Output Format (MANDATORY)

At the end of each phase, the agent outputs:

## Phase N Summary
- What changed:
- Files touched:
- Tests run:
- Results:
- Next:

At the end of the task, include:
- Final Done checklist (Definition of Done items)
- PR-ready summary (what/why/how)
- How to verify manually (2-5 steps)
