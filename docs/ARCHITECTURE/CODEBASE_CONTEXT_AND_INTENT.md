> NOTICE: Review the active task plan before implementing task-related changes.

> Active task compliance plan: `docs/refactor/Map_Task_5of5_Finalization_Plan_2026-02-13.md`

# CODEBASE_CONTEXT_AND_INTENT.md

## Purpose

This document is a **normative implementation-intent contract**.

It exists so contributors and AI/dev tools can:
- Understand what behavior must be preserved
- Understand hard system constraints
- Implement new work without ad-hoc logic
- Preserve determinism, layering, and single-source-of-truth design

If a code change conflicts with this document, the change is wrong even if it compiles.

This document must be read together with:
- `ARCHITECTURE.md`
- `CODING_RULES.md`
- `CONTRIBUTING.md`

Related implementation specs:
- `<PHASED_IMPLEMENTATION_PLAN>.md`
- `<QUALITY_MODEL_SPEC>.md`
- `<REGRESSION_TEST_PLAN>.md`

---

## Core Principles

1) Deterministic behavior is mandatory.
- Domain logic must not depend on wall clock or UI state.
- Replay/simulation behavior must remain reproducible.

2) Single source of truth is mandatory.
- Authoritative state must live in repositories/state owners.
- Presentation layers must render, not decide policy.

3) Domain logic stays in domain.
- Business and signal-processing math belong in use cases/services.
- UI may smooth visuals but must not redefine system truth.

4) Honest outputs over fabricated precision.
- Invalid/unknown/degraded output is better than confident wrong output.

---

## Constraint Model (Non-Negotiable)

This section defines hard constraints, not stretch goals.

1) Sensor and data limits must be treated as real limits.
- Do not infer unavailable ground truth as if directly measured.

2) Observability may degrade by operating mode.
- Some states or maneuvers reduce estimate quality.
- Quality-aware gating and hold behavior must account for this.

3) Uncertainty must be explicit.
- Confidence/quality must be modeled as first-class data.
- Downstream behavior must consume those signals directly.

---

## Intent for New Work

All new feature work must:

1) Add explicit quality/confidence modeling where estimates are derived.
2) Gate derived outputs on confidence thresholds and stability rules.
3) Prefer persistent, slow-changing state when instant estimates are unreliable.
4) Use anti-flicker behavior (dwell/hysteresis/hold) for user-visible quality states.
5) Preserve backward-compatible behavior where intent says "must preserve."

---

## Architecture Mapping (Where Changes Belong)

**Repository/State Layer**
- Persist and expose authoritative state
- Compute freshness/staleness using injected timebase
- Publish observable state streams

**Domain Layer**
- Compute confidence and validity
- Apply gating, tiering, and fallback policy
- Produce authoritative derived outputs

**UI Layer**
- Render values, labels, and quality markers
- Apply optional non-authoritative visual easing
- Must not own authoritative business state

Cross-layer leakage is not allowed.

---

## Forbidden Implementation Patterns

1) Do not substitute unrelated signals to mask invalid state.
2) Do not push business math into UI for convenience.
3) Do not add global mutable singletons when DI should be used.
4) Do not use scattered undocumented thresholds.
5) Do not use non-deterministic time sources in deterministic domain paths.

---

## Verification and Regression Gates

Required quality gates for each phase/change set:

1) Deterministic replay/regression tests for key scenarios
2) Stability tests for mode/quality transitions
3) Unit tests for gating/tiering/fallback rules
4) Validation that degraded states are explicit and stable

Silent quality regressions are unacceptable.

---

## Minimal Handoff Instructions

Contributors/agents should:

1) Read `ARCHITECTURE.md`, then `CODING_RULES.md`, then this file.
2) Execute `docs/refactor/Map_Task_5of5_Finalization_Plan_2026-02-13.md` in strict phase order.
3) Add/adjust tests at each phase.
4) Keep `KNOWN_DEVIATIONS.md` empty unless explicitly approved.
5) Run required build/test checks after each phase.

If blocked by missing code context:
- Add a short architecture or pipeline map document for the affected area, then continue.

---

## End State

The codebase should behave like a production-grade engineered system:
- Deterministic
- Layer-correct
- Explicit about uncertainty
- Stable under noisy conditions
- Regression-resistant over time
