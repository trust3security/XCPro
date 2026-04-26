# CHANGE_PLAN_TEMPLATE.md

## Purpose

Use this template before implementing any non-trivial feature or refactor.
The goal is to prevent architecture/rules regressions before code changes begin.

Read first:

1. `ARCHITECTURE.md`
2. `CODING_RULES.md`
3. `PIPELINE.md`
4. `CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title:
- Owner:
- Date:
- Issue/PR:
- Status: Draft | Approved | In progress | Complete

## 1) Scope

- Problem statement:
- Why now:
- In scope:
- Out of scope:
- User-visible impact:
- Rule class touched: Invariant | Default | Guideline

## 1A) Confirmed Boundaries / Verified Facts (Mandatory)

List verified repo/system facts that shape the plan.

Rules:

- If a fact can be checked in repo docs, code, configs, tests, or local system
  state, verify it first.
- Do not create or rely on assumptions. If something is not verified or
  explicitly decided, list it in unresolved decisions instead.
- Cite the source of truth when the owner or boundary is non-obvious.

| Fact | Source of Truth | Why It Matters |
|---|---|---|
| | | |

## 1B) Explicit Decisions / Defaults Chosen (Mandatory)

List only decisions/defaults backed by the user, repo docs, code contracts, or
verified local evidence. Do not invent defaults.

If there are none, write `None`.

| Decision / Default | Source of Decision | Impact If Wrong | Follow-up / Owner |
|---|---|---|---|
| | | | |

## 1C) Unresolved Decisions / Questions (Mandatory)

List decisions that still need an answer before or during implementation.

If there are none, write `None`.

| Decision / Question | Why It Matters | Owner / Decision Maker | Blocking? | Resolution Plan |
|---|---|---|---|---|
| | | | | |

## 2) Architecture Contract

### 2.1 SSOT Ownership

List each affected data item and one authoritative owner.

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| | | | |

### 2.1A State Contract (Mandatory for new or changed state)

Document the full contract for each authoritative or derived state item.

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| | | | | | | | | |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
- Any boundary risk:

### 2.2A Reference Pattern Check (Mandatory)

List 1-2 similar existing features/files reviewed before implementation.

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| | | | |

If no suitable reference exists:

- State that explicitly.
- Describe the smallest architecture-consistent structure that will be used.

### 2.2B Boundary Moves (Mandatory)

List each responsibility that changes ownership.

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| | | | | |

### 2.2C Bypass Removal Plan (Mandatory)

List direct-call bypasses being removed (or explicitly retained with rationale).

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| | | | |

### 2.2D File Ownership Plan (Mandatory)

For each file to create or modify, declare the intended owner.

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| | | | | | |

Rules:

- If a file is already large or mixed-purpose, split by responsibility before extending it.
- Prefer several focused files over one broad mixed-responsibility file.
- If a split is required, name the planned focused files here.

### 2.2E Module and API Surface (Mandatory when boundaries change)

List each new or changed public/cross-module contract.

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| | | | | | |

### 2.2F Scope Ownership and Lifetime (Mandatory when adding a long-lived scope)

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| | | | | |

### 2.2G Compatibility Shim Inventory (Mandatory when shims/bridges remain)

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| | | | | | |

### 2.2H Canonical Formula / Policy Owner (Mandatory when math/constants/policy are touched)

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| | | | | |

### 2.2I Stateless Object / Singleton Boundary (Mandatory when adding a new `object` or singleton-like holder)

| Object / Holder | Why `object` / Singleton Is Needed | Mutable State? | Why It Is Non-Authoritative | Why Not DI-Scoped Instance? | Guardrail / Test |
|---|---|---|---|---|---|
| | | | | | |

### 2.3 Time Base

For each time-dependent value, declare time base explicitly.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| | | |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
- Primary cadence/gating sensor:
- Hot-path latency budget:

### 2.4A Logging and Observability Contract (Mandatory when logging changes touch hot paths, privacy-sensitive data, or platform edges)

| Boundary / Callsite | Logger Path (`AppLogger` / Platform Edge) | Sensitive Data Risk | Gating / Redaction | Temporary Removal Plan |
|---|---|---|---|---|
| | | | | |

### 2.5 Replay Determinism

- Deterministic for same input: Yes/No
- Randomness used: Yes/No (if yes, how seeded)
- Replay/live divergence rules:

### 2.5A Error and Degraded-State Contract (Mandatory when non-happy-path behavior changes)

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| | | | | | |

### 2.5B Identity and Model Creation Strategy (Mandatory when IDs/timestamps are created or changed)

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| | | | | |

### 2.5C No-Op / Test Wiring Contract (Mandatory when NoOp or convenience constructors are used)

| Class / Boundary | NoOp / Convenience Path | Production Allowed? | Safe Degraded Behavior | Visibility / Guardrail |
|---|---|---|---|---|
| | | | | |

### 2.6 Enforcement Coverage (Mandatory)

Map each architecture rule risk to an automated guard.

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| | | | |

### 2.7 Visual UX SLO Contract (Mandatory for map/overlay/replay interaction changes)

Map user-visible outcomes to measurable SLOs.

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| | | | | | |

## 3) Data Flow (Before -> After)

Describe end-to-end flow as text:

```
Source -> Repository (SSOT) -> UseCase -> ViewModel -> UI
```

## 4) Implementation Phases

For each phase, define:

- Goal
- Files to change
- Ownership/file split changes in this phase
- Tests to add/update
- Exit criteria

## 5) Test Plan

- Unit tests:
- Replay/regression tests:
- UI/instrumentation tests (if needed):
- Degraded/failure-mode tests:
- Boundary tests for removed bypasses:
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | |
| Time-base / replay / cadence | Fake clock + deterministic repeat-run tests | |
| Persistence / settings / restore | Round-trip / restore / migration tests | |
| Ownership move / bypass removal / API boundary | Boundary lock tests | |
| UI interaction / lifecycle | UI or instrumentation coverage | |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| | | | |

## 6A) ADR / Durable Decision Record

- ADR required: Yes/No
- ADR file:
- Decision summary:
- Why this belongs in an ADR instead of plan notes:

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No implementation path depends on an unverified fact or undecided product choice
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- Error/degraded-state behavior is explicit and tested where behavior changed
- Ownership/boundary/public API decisions are captured in an ADR when required
- For map/overlay/replay interaction changes: impacted visual SLOs pass
  (or approved deviation is recorded with issue/owner/expiry)
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry)

## 8) Rollback Plan

- What can be reverted independently:
- Recovery steps if regression is detected:
