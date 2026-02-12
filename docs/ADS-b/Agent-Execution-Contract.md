
# Agent-Execution-Contract.md -- Autonomous Implementation Skeleton

Use this file when you want Codex to implement a task autonomously end-to-end.
It is a reusable skeleton. Fill in Section 1 and Section 3 for your specific task.

---

# Quick Usage

Copy/paste this prompt pattern when starting work:

```text
Use `docs/ADS-b/Agent-Execution-Contract.md` as the execution skeleton.
Implement this autonomously end-to-end without waiting for per-step approval.

Change Request:
<paste Section 1 details here, or reference a plan doc>

Acceptance Criteria:
<paste Section 3 details here>
```

If something is missing, Codex should infer from repo context, make explicit assumptions, and continue.

---

# 0) Agent Execution Contract (Read First)

This document is the task-level execution contract.
The executing agent (Codex) owns delivery from baseline to verification.

## 0.1 Authority
- Proceed phase by phase without asking for confirmation.
- Ask questions only when blocked by missing external decisions or unavailable inputs.
- If ambiguity exists, choose the most repo-consistent option and record assumptions.

## 0.2 Responsibilities
- Implement Section 1 fully.
- Preserve architecture constraints in `docs/ARCHITECTURE/*`.
- Keep domain/business logic testable and out of UI.
- Use explicit time bases:
  - Monotonic for elapsed/staleness calculations.
  - Replay timestamps for replay simulation logic.
  - Wall time only for UI labels/persistence where appropriate.
- Update docs when wiring/rules/policies change.
- Run required checks and fix failures caused by the change.

## 0.3 Workflow Rules
- Work in ordered phases (Section 2).
- Do not leave partial production paths or TODO placeholders.
- Keep diffs focused; avoid unrelated edits.
- If tests change, justify whether behavior changed or parity is preserved.

## 0.4 Definition of Done
Done means all are true:
- Section 2 phases completed.
- Section 3 acceptance criteria satisfied.
- Section 4 required checks passed (or blockers explicitly documented).
- Section 5 decision log updated for non-trivial design choices.

---

# 1) Change Request (Human Fills This In)

## 1.1 Summary (1-3 sentences)
- [ ] What should be built or changed?

## 1.2 User Value / Use Cases
- [ ] As a ___, I want ___, so that ___.
- [ ] As a ___, I want ___, so that ___.

## 1.3 Scope
- In scope:
  - [ ] ___
- Out of scope:
  - [ ] ___

## 1.4 Constraints
- Modules/layers affected:
  - [ ] ___
- Performance/battery limits:
  - [ ] ___
- Backward compatibility/migrations:
  - [ ] ___
- Compliance/safety rules:
  - [ ] ___

## 1.5 Inputs and Outputs
- Inputs (events/data/sensors/APIs):
  - [ ] ___
- Outputs (UI/state/storage/logging):
  - [ ] ___

## 1.6 Behavior Parity Checklist (for refactors/replacements)
- [ ] List behaviors that must stay identical.

## 1.7 References
- Plan docs / specs:
  - [ ] `docs/...`
- Related code paths:
  - [ ] `path/to/file`

---

# 2) Execution Plan (Agent Owns Execution)

## Phase 0 - Baseline
- Map current behavior and entry points.
- Identify invariants and architecture boundaries.
- Add or confirm safety-net tests where needed.

Gate:
- No intentional behavior change yet.

## Phase 1 - Core Logic
- Implement core behavior with testable design.
- Add/adjust unit tests for logic and edge cases.

Gate:
- Core tests pass.

## Phase 2 - Integration
- Wire DI/repository/use case/viewmodel/ui as required.
- Add integration or viewmodel tests when applicable.

Gate:
- Feature works end to end in debug build.

## Phase 3 - Hardening
- Handle lifecycle/threading/cancellation/failure cases.
- Remove dead code and update required docs.

Gate:
- Required checks pass.

## Phase 4 - Delivery Summary
- Final consistency pass (readability, scope, docs).
- Produce implementation summary and verification evidence.

Gate:
- Definition of Done met.

---

# 3) Acceptance Criteria (Human Defines, Agent Must Satisfy)

## 3.1 Functional Criteria
- [ ] Given ___, when ___, then ___.
- [ ] Given ___, when ___, then ___.

## 3.2 Edge Cases
- [ ] Empty/missing inputs.
- [ ] Lifecycle transitions (background/restore/restart) when relevant.
- [ ] Error and retry behavior.

## 3.3 Required Test Coverage
- [ ] Unit tests for domain/core logic.
- [ ] ViewModel/integration tests where behavior crosses layers.
- [ ] Replay/determinism checks where applicable.

---

# 4) Required Checks (Agent Must Run and Report)

Repo baseline checks:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

When relevant and environment allows:
- `./gradlew connectedDebugAndroidTest`

Agent report must include:
- Commands executed.
- Pass/fail per command.
- Fixes applied for failures.
- Any checks not run and why.

---

# 5) Decision Log / ADR Notes

Record non-trivial decisions made during implementation:
- Decision:
- Alternatives considered:
- Why chosen:
- Risks/impact:
- Follow-up work:

---

# 6) Required Output Format

At each phase end, the agent reports:

## Phase N Summary
- What changed:
- Files touched:
- Tests/checks run:
- Results:
- Next:

At task end, include:
- Final Done checklist.
- PR-ready summary (what/why/how).
- Manual verification steps (2-5 steps).

