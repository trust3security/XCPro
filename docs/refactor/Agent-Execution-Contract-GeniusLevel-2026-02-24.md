# Agent-Execution-Contract-GeniusLevel-2026-02-24.md

## Purpose

This contract defines how autonomous agents execute the
`Genius_Level_Codebase_Refactor_Plan_2026-02-24.md` campaign.

Map/task slices are governed by the newer, stricter campaign contract:

- `docs/refactor/Agent-Execution-Contract-MapTask-2026-02-25.md`

If scope includes map/task files, that contract's priority and phase order apply in addition to this campaign-level contract.

This contract is campaign-specific.
Global hierarchy remains:

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/AGENT.md`
5. this file

If any rule conflicts, the higher item in hierarchy wins.

## 0) Mission

Deliver refactor and hardening work to move the codebase to quantified
"genius-level" quality targets without architecture drift.

## 1) Mandatory Startup Gate (Per Workstream)

Before changing code, the agent must:

1. Read:
   - `AGENTS.md`
   - `docs/ARCHITECTURE/ARCHITECTURE.md`
   - `docs/ARCHITECTURE/CODING_RULES.md`
   - `docs/ARCHITECTURE/PIPELINE.md`
   - `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
   - `docs/ARCHITECTURE/CONTRIBUTING.md`
   - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
   - `docs/refactor/Genius_Level_Codebase_Refactor_Plan_2026-02-24.md`
2. Declare:
   - targeted phase
   - SSOT ownership affected
   - dependency direction check
   - time base declaration for touched values
3. Capture baseline for touched scope:
   - `./gradlew enforceRules`
   - `./gradlew testDebugUnitTest`
   - `./gradlew assembleDebug`
   - `./gradlew lintDebug` (or module-specific lint if scoped)

If baseline cannot be obtained, agent must report exact blocker and continue
with the most architecture-safe path.

## 2) Non-Negotiables

The agent must never:

- violate MVVM + UDF + SSOT layering.
- put domain/business logic in UI or ViewModel.
- use direct system wall-time in domain/fusion logic.
- introduce hidden mutable global state.
- bypass use-case boundaries with raw manager/controller exposure.
- add undocumented architectural deviations.

## 3) Execution Model (Strict Phases)

For each phase batch:

### Phase A: Plan Slice

- Define one small slice (single concern).
- List files and intended tests.
- Define explicit pass/fail gate.

### Phase B: Implement

- Apply minimal cohesive edits for the slice.
- Prefer extracting collaborators over enlarging files.
- Keep behavior parity unless explicitly planned.

### Phase C: Verify

Run, at minimum:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run when relevant:

```bash
./gradlew lintDebug
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

### Phase D: Self-Audit

Agent must explicitly verify:

- no UI imports in domain paths touched.
- no data/repository leaks into UI.
- no direct time calls in domain/fusion touched.
- no new escape-hatch APIs.
- no new file-size hotspot against plan budgets.

### Phase E: Report

Return structured summary:

- What changed
- Files touched
- Tests/commands run + pass/fail
- Risks or residual debt
- Updated quality rescore deltas

### Phase F: Mandatory Re-Pass Loop (6 Passes)

When a re-pass is requested, the agent must run exactly six focused passes:

1. Architecture boundaries (MVVM/UDF/SSOT/DI direction)
2. Correctness and behavior regressions
3. Time base and replay determinism
4. Concurrency/state/race and lifecycle safety
5. Tests and verification depth
6. Docs, pipeline sync, and hygiene

Findings must be reported in severity order with file references.

### Phase G: Implement-After-Findings Rule

If the request is "find and advise" plus "implement":

- do not stop at findings only.
- implement all High and Medium findings that are in-scope and architecture-safe.
- if a finding is blocked or ambiguous, document the blocker and propose the
  narrowest safe follow-up.
- re-run verification after implementation and report deltas.

### Phase H: Verification-Blocker Fallback (Windows/IO Lock Cases)

If required verification is blocked by non-code environment issues
(for example locked Gradle test output files on Windows):

- retry once with daemon/process cleanup and stale test-output cleanup.
- if still blocked, run focused verification for touched scope:
  - `./gradlew enforceRules`
  - `./gradlew :feature:map:compileDebugKotlin` (or equivalent touched module compile)
  - targeted unit tests for changed classes/use-cases/repositories/viewmodels
  - `./gradlew assembleDebug`
- report the exact blocker, commands attempted, and what remains unverified.
- do not claim full-gate pass if `testDebugUnitTest` did not complete.

## 4) Autonomy and Decision Policy

Agent should proceed without confirmation for standard refactor actions.
Agent must ask only when:

- requested behavior is ambiguous and multiple outcomes are product-visible.
- destructive operations are required (history rewrite, mass deletion).
- a rule-compliant path is impossible without approved deviation.

## 5) Deviation Protocol

If a required rule cannot be satisfied in-scope:

1. open/update `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
2. include issue ID, owner, expiry date, and narrow scope
3. explain why no compliant alternative was feasible
4. add explicit removal follow-up task

No silent deviations are allowed.

## 6) Batch Size and Safety Limits

- Prefer slices <= 400 LOC changed.
- Prefer 1-2 architectural concerns per batch.
- Keep each batch independently revertable.
- Do not mix unrelated cleanup into focused slices.

## 7) Quality Rescore Requirements

After each meaningful batch, rescore with evidence:

- Architecture cleanliness: x/5
- Maintainability/change safety: x/5
- Test confidence on risky paths: x/5
- Overall map/task slice quality: x/5
- Release readiness: x/5

Rules:

- scores must cite concrete files/tests/guards.
- if any score < 4.0, include remediation plan in next batch.
- generic "5/5" without evidence is invalid.

## 8) Definition of Done (Campaign)

Campaign is complete only when all are true:

- acceptance gates from
  `docs/refactor/Genius_Level_Codebase_Refactor_Plan_2026-02-24.md` are met.
- no unresolved blocking lint errors.
- required build/test gates pass.
- architecture drift checks are clean.
- docs are synchronized with actual ownership/pipeline behavior.

## 9) Final Deliverable Format

At campaign completion, agent must provide:

1. Done checklist against plan acceptance gates
2. Final quality rescore with evidence
3. PR-ready summary (what/why/impact)
4. Manual verification steps (2-5 steps)
5. Residual risks and explicit next actions
