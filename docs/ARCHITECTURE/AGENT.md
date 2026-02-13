# AGENT.md --- Autonomous Execution Contract (XCPro)

This file defines the required execution model for any autonomous coding
agent (Codex or equivalent) working in this repository.

This is a task-level execution harness. `AGENTS.md` remains the global
repository contract.

------------------------------------------------------------------------

# 0) Execution Authority

The agent must:

-   Execute changes end-to-end without requesting confirmation.
-   Follow phases strictly in order.
-   Never violate ARCHITECTURE.md or CODING_RULES.md.
-   Prefer architectural correctness over minimal diff size.
-   Document assumptions when ambiguity exists.

If a rule is unclear, consult: 1. ARCHITECTURE.md 2. CODING_RULES.md 3.
PIPELINE.md 4. CHANGE_PLAN_TEMPLATE.md

If still unclear, choose the most architecture-consistent option and
document it.

------------------------------------------------------------------------

# 1) Mandatory Pre-Implementation Gate

Before writing code, the agent must explicitly define:

## 1.1 SSOT Ownership

For each new or modified data item: - Authoritative owner: - Exposed
as: - Forbidden duplicates:

If SSOT cannot be identified, implementation must not proceed.

## 1.2 Dependency Direction

Confirm dependency flow remains:

UI → domain → data

No upward leaks allowed.

## 1.3 Time Base Declaration (MANDATORY)

For every time-dependent value:

| Value \| Time Base (Monotonic / Replay / Wall) \| Why \|

Explicitly forbidden: - Monotonic vs wall comparison - Replay vs wall
comparison - Direct System.currentTimeMillis usage in domain/fusion

## 1.4 Replay Determinism

Declare: - Deterministic for same input? (Yes/No) - Randomness used? (If
yes, how seeded) - Replay/live divergence rules

## 1.5 Boundary Adapter Check

If touching: - Persistence - Sensors - Network - File I/O - Device APIs

Confirm: - Domain defines interface - Data layer implements - Inject via
DI

If skipped, justify.

------------------------------------------------------------------------

# 2) Phased Execution Model

## Phase 0 --- Baseline

-   Identify affected flow in PIPELINE.md.
-   Add or confirm regression tests locking current behavior.
-   Repo builds with no behavior changes.

Gate: build + tests pass.

------------------------------------------------------------------------

## Phase 1 --- Pure Logic Implementation

-   Extract or implement business logic in UseCases.
-   No Android imports.
-   Injected clocks only.
-   Add unit tests.

Gate: deterministic tests pass.

------------------------------------------------------------------------

## Phase 2 --- Repository / SSOT Wiring

-   Ensure single authoritative owner.
-   Replace direct state mirrors.
-   Expose Flow/StateFlow only.
-   No ViewModel persistence.

Gate: no duplicated state.

------------------------------------------------------------------------

## Phase 3 --- ViewModel + UI Wiring

-   ViewModel consumes UseCases only.
-   No business logic in UI.
-   No manager/controller escape hatches.
-   Lifecycle-aware flow collection.

Gate: end-to-end works in debug.

------------------------------------------------------------------------

## Phase 4 --- Hardening

-   Threading verified.
-   No Main-thread heavy work.
-   Timebase explicit in code + tests.
-   Replay deterministic.
-   Update PIPELINE.md if flow changed.
-   Update CHANGE_PLAN if architecture moved.

Gate: required checks pass.

------------------------------------------------------------------------

# 3) Acceptance Criteria

Must satisfy:

-   No architecture rule violations.
-   No duplicated SSOT ownership.
-   No raw manager/controller exposure.
-   Deterministic replay for identical inputs.
-   No wall-time usage in domain logic.
-   ViewModels contain no business math.
-   No Compose runtime state in non-UI managers.
-   No new entrypoint hacks.

If any violation exists: - Fix it OR - Record in KNOWN_DEVIATIONS.md
with issue ID, owner, expiry.

------------------------------------------------------------------------

# 4) Required Verification

Minimum:

./gradlew enforceRules ./gradlew testDebugUnitTest ./gradlew
assembleDebug

When relevant:

./gradlew connectedDebugAndroidTest

Agent must report: - Commands run - Pass/fail - Fixes applied

------------------------------------------------------------------------

# 5) Architecture Drift Detection (MANDATORY SELF-AUDIT)

Before marking complete, the agent must verify:

-   No UI imports in domain.
-   No data imports in UI.
-   No direct time calls in domain.
-   No new global mutable state.
-   No new god-objects introduced.
-   No bypass of use-case layer.
-   No manager exposing MapLibre types.
-   No duplication of trail/task/flight state.

If drift detected, fix before completion.

------------------------------------------------------------------------

# 6) Quality Rescore (MANDATORY)

After completing all phases (or major workstreams), rescore:

-   Architecture cleanliness: \_\_ / 5
-   Maintainability / change safety: \_\_ / 5
-   Test confidence on risky paths: \_\_ / 5
-   Overall map/task slice quality: \_\_ / 5
-   Release readiness (map/task slice): \_\_ / 5

Each score must include: - Evidence (files changed, rules enforced,
tests added) - Remaining risks - If \< 4.0, explain why

Scores must be evidence-based. Generic "5/5" responses are invalid.

------------------------------------------------------------------------

# 7) Output Format (MANDATORY)

At end of each phase:

## Phase N Summary

-   What changed:
-   Files touched:
-   Tests added/updated:
-   Verification results:
-   Risks:

At final completion:

-   Done checklist (Section 3 satisfied)
-   Quality Rescore (Section 6)
-   PR-ready summary (what / why / architectural impact)
-   Manual verification steps (2--5 steps)

------------------------------------------------------------------------

# 8) Final Rule

Architecture is not optional.

If correctness and convenience conflict, correctness wins.

If speed and determinism conflict, determinism wins.

If minimal diff and proper layering conflict, proper layering wins.
