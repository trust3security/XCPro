# AGENT.md -- Autonomous Engineering Execution Template

Use this file as the **single source of truth** when asking an agent (Codex) to implement a change end-to-end.
You (the human) fill in the Change Request" and any project-specific notes. The agent must follow the contract below.

---

# 0) Agent Execution Contract (READ FIRST)

This document is the authoritative specification for this work.

The executing agent (Codex) is responsible for implementing the requested change **from start to finish** as a self-directed software engineering task.

## 0.1 Authority
- Proceed through all phases **without asking for confirmation**.
- **Do not ask questions** unless blocked by genuinely missing information that cannot be inferred from the repository.
- If ambiguity exists, choose the most reasonable repo-consistent option and **document the assumption** in commits and/or PR notes.

## 0.2 Responsibilities
- Implement the change described in **Section 1 (Change Request)**.
- Run builds, unit tests, and lint locally.
- Fix all build/test/lint failures encountered.
- Preserve existing user-visible behavior unless explicitly stated otherwise.
- Keep business logic pure and unit-testable (no Android framework calls in core logic unless the feature requires it).
- Prefer deterministic, injectable time sources:
  - Use **monotonic time** for staleness/elapsed logic.
  - Use **wall time** only for display timestamps.
  - Do not mix time sources in a single decision path.

## 0.3 Workflow Rules
- Work phase-by-phase, in order.
- Commit after each phase with a clear, scoped message.
- Do not leave TODOs or partial implementations in production paths.
- If an existing test must change, justify it strictly as behavior parity or updated requirements (cite Section 1).

## 0.4 Definition of Done
Work is complete only when:
- All phases in **Section 2 (Execution Plan)** are implemented.
- Tests cover the behavior checklist in **Section 3 (Acceptance Criteria)**.
- Required commands in **Section 4 (Required Checks)** pass.
- The change is documented in **Section 5 (Notes / ADR)** if new decisions were made.

---

# 1) Change Request (HUMAN FILLS THIS IN)

## 1.1 Feature Summary (1-3 sentences)
- [ ] Describe what you want built.

## 1.2 User Stories / Use Cases
- [ ] As a ___, I want ___, so that ___.
- [ ] As a ___, I want ___, so that ___.

## 1.3 Non-Goals (explicitly out of scope)
- [ ] What this change must NOT do.

## 1.4 Constraints
- Platforms / modules:
  - [ ] Android app module(s):
  - [ ] Libraries / layers affected:
- Performance / battery:
  - [ ] Any budget limits?
- Backwards compatibility:
  - [ ] Must preserve existing settings / migrations?
- Safety / compliance:
  - [ ] Any rules that must not be violated?

## 1.5 Inputs / Outputs
- Inputs:
  - [ ] Events / sensors / data sources:
- Outputs:
  - [ ] UI changes:
  - [ ] Data stored:
  - [ ] Logs / metrics:

## 1.6 Behavior Parity" Checklist (if refactor or replacements)
- [ ] List behaviors that must remain identical.

---

# 2) Execution Plan (AGENT OWNS THIS, BUT MAY EDIT FOR REALITY)

## Phase 0 -- Baseline & Safety Net
- Locate relevant code paths, entry points, and current behavior.
- Add/confirm tests that lock current behavior (when applicable).
- Document current defaults and edge cases.

**Gate:** no functional changes; repo builds.

## Phase 1 -- Core Implementation
- Implement the minimal viable slice of the change.
- Keep business logic testable and isolated.
- Add unit tests for core logic and edge cases.

**Gate:** unit tests pass; behavior matches Section 3.

## Phase 2 -- Integration & UI Wiring
- Connect the feature into the app architecture (DI, VM, flows, etc.).
- Implement UI changes.
- Add integration tests (if you have them) or VM tests.

**Gate:** feature works end-to-end in debug build.

## Phase 3 -- Hardening
- Handle failures, lifecycles, cancellation, threading.
- Add logging behind debug flags if needed.
- Remove dead code; update docs.

**Gate:** all required checks pass (Section 4).

## Phase 4 -- Polish & PR Readiness
- Ensure code style, readability, and minimal diffs.
- Update changelog / release notes if your repo uses them.
- Provide a short PR summary and testing evidence.

**Gate:** Definition of Done satisfied.

---

# 3) Acceptance Criteria (HUMAN DEFINES, AGENT MUST SATISFY)

## 3.1 Functional Acceptance Criteria
- [ ] Given ___ when ___ then ___.
- [ ] Given ___ when ___ then ___.

## 3.2 Edge Cases
- [ ] Offline / no permissions / no sensors / empty data.
- [ ] Rotation / background / process death (if applicable).
- [ ] Error handling / retries.

## 3.3 Test Coverage Required
- [ ] Unit tests for core logic.
- [ ] VM tests for state transitions (if applicable).
- [ ] Any golden/screenshot tests (if applicable).

---

# 4) Required Checks (AGENT MUST RUN AND PASS)

Minimum (edit to match your repo):
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`

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
- Decision:
- Alternatives considered:
- Why chosen:
- Impact / risks:
- Follow-ups:

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
- Final Done" checklist (Definition of Done items)
- PR-ready summary (what/why/how)
- How to verify manually (2-5 steps)

