# Automate_Quality_Gates_Plan_2026-03-01.md

## Purpose

Phased implementation plan for architecture/timebase quality gate automation and documentation sync.
This plan is based on:

- `docs/ARCHITECTURE/CODEX_TASK_AUTOMATE_QUALITY_GATES.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: Automate Quality Gates and Documentation Sync
- Owner: XCPro Team (Codex session 2026-03-01)
- Date: 2026-03-01
- Issue/PR: Local implementation session (2026-03-01)
- Status: Complete

## 1) Scope

- Problem statement:
  - Architecture/timebase policy exists but can drift without consistent automated enforcement and aligned docs.
- Why now:
  - Direct-time-call regressions and doc drift increase risk in fusion/replay paths and CI predictability.
- In scope:
  - `.github/workflows/quality-gates.yml`
  - `scripts/arch_gate.py`
  - Update architecture governance docs listed in task brief
  - Confirm and document real time abstraction anchors:
    - `core/time/src/main/java/com/example/xcpro/core/time/Clock.kt`
    - `app/src/main/java/com/example/xcpro/di/TimeModule.kt`
- Out of scope:
  - Broad feature behavior changes unrelated to quality gates/docs
  - Unrelated module refactors
- User-visible impact:
  - None directly in app UX; impacts developer workflow and CI pass/fail behavior.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Architecture policy | `docs/ARCHITECTURE/ARCHITECTURE.md` + `docs/ARCHITECTURE/CODING_RULES.md` | Versioned docs | Conflicting policy text in feature docs |
| Deviation status ledger | `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` | Versioned docs | Deviation summary replicated in `README.md` |
| Local static-gate policy | `scripts/arch_gate.py` | CLI result + CI step | Parallel, undocumented duplicate scripts |
| CI enforcement contract | `.github/workflows/quality-gates.yml` | GitHub Actions checks | Divergent CI jobs for same architecture rules |

### 2.2 Dependency Direction

`UI -> domain -> data` remains unchanged.

- Modules/files touched:
  - Docs, CI workflow, static gate script, and targeted production callsites only when needed for compliance.
- Any boundary risk:
  - Medium risk if enforcement rules are tightened without updating known exceptions/deviations.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Direct-time-call detection | Manual review + partial checks | `scripts/arch_gate.py` + CI workflow | Make failures deterministic and repeatable | `python scripts/arch_gate.py` + CI run |
| Gate execution policy | Ad-hoc local commands | Explicit PR loop in docs + workflow | Reduce ambiguity for contributors and agents | `CONTRIBUTING.md` + workflow job |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Production Kotlin direct time API calls outside adapters | `System.currentTimeMillis`, `SystemClock.*`, `Date(...)`, etc. | Injected `Clock`/approved adapter wrappers | Phase 4 |
| Missing architecture gate execution in PR flow | Optional/manual run | Required CI step + documented local commands | Phase 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Domain/fusion deltas and validity windows | Monotonic | Deterministic sensor timing and safe delta math |
| Replay timestamps and sequencing | Replay | Deterministic replay behavior |
| UI labels/export/persistence timestamps | Wall | Human-readable output and storage boundary |
| Gate execution timing (CI/local runtime) | Wall | Tooling runtime only, not domain logic |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - No architecture change required for this plan.
- Primary cadence/gating sensor:
  - No sensor cadence change in this plan.
- Hot-path latency budget:
  - No additional runtime hot-path work; only static checks/docs/CI.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (must remain true)
- Randomness used: No new randomness
- Replay/live divergence rules:
  - Unchanged; enforcement/docs only.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Direct time APIs reintroduced in production code | `ARCHITECTURE.md` timebase rules, `CODING_RULES.md` 1A | Static gate + CI | `scripts/arch_gate.py`, `.github/workflows/quality-gates.yml` |
| Architecture docs drift from enforced behavior | `CODEBASE_CONTEXT_AND_INTENT.md` invariants | Doc review + PR checklist | Updated docs in same change set |
| Deviation truth split across files | `KNOWN_DEVIATIONS.md` policy | Doc gate/review | `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`, `docs/ARCHITECTURE/README.md` |
| Regression slips past local-only validation | `AGENT.md` required verification | CI + local command contract | `quality-gates.yml`, `CONTRIBUTING.md` |

## 3) Data Flow (Before -> After)

Before:

```
Contributor change -> optional local checks -> PR -> possible late architecture drift detection
```

After:

```
Contributor change
-> local gates (arch_gate + enforceRules + unit + assemble)
-> PR CI quality-gates workflow
-> deterministic pass/fail against documented architecture rules
```

## 4) Implementation Phases

### Phase 0 - Baseline and Inventory

- Goal:
  - Capture current violations, current docs state, and current CI coverage.
- Files to change:
  - None expected (baseline evidence only).
- Tests to add/update:
  - None.
- Exit criteria:
  - Baseline commands executed and recorded:
    - `python scripts/arch_gate.py`
    - `./gradlew enforceRules`
    - `./gradlew testDebugUnitTest`
    - `./gradlew assembleDebug`

### Phase 1 - Documentation Alignment

- Goal:
  - Update the required architecture/governance docs to reflect enforceable policy and real anchors.
- Files to change:
  - `docs/ARCHITECTURE/AGENT.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/CONTRIBUTING.md`
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/README.md`
- Tests to add/update:
  - None.
- Exit criteria:
  - No contradictory policy text across updated docs.
  - Time abstraction anchors documented with real paths.
  - Deviation ledger ownership clearly declared in `KNOWN_DEVIATIONS.md`.

### Phase 2 - Local Static Gate Hardening

- Goal:
  - Implement/update `scripts/arch_gate.py` for explicit production-time API enforcement.
- Files to change:
  - `scripts/arch_gate.py`
- Tests to add/update:
  - Script-level verification by running gate against repo.
- Exit criteria:
  - Gate reports file:line violations clearly.
  - Gate excludes tests/tooling paths and returns non-zero on violations.

### Phase 3 - CI Quality Gates

- Goal:
  - Add/update CI workflow that enforces required local checks on PRs/pushes.
- Files to change:
  - `.github/workflows/quality-gates.yml`
- Tests to add/update:
  - Workflow dry-run review (syntax + command parity with local).
- Exit criteria:
  - Workflow runs:
    - `python scripts/arch_gate.py`
    - `./gradlew enforceRules`
    - `./gradlew testDebugUnitTest`
    - `./gradlew assembleDebug`
  - Triggered on `pull_request` and push to default branch targets.

### Phase 4 - Clock/TimeSource Compliance Tightening

- Goal:
  - Replace any remaining direct-time calls in production Kotlin outside approved adapters.
- Files to change:
  - Targeted production Kotlin files discovered by gate scan.
  - Optional adapter files with explicit rationale.
- Tests to add/update:
  - Unit tests for time-dependent paths when behavior or timing logic is touched.
- Exit criteria:
  - `scripts/arch_gate.py` passes.
  - Any temporary exceptions are recorded with issue/owner/expiry in `KNOWN_DEVIATIONS.md`.

### Phase 5 - Final Verification and Closeout

- Goal:
  - Prove end-to-end compliance and document completion evidence.
- Files to change:
  - Optional PR summary docs only.
- Tests to add/update:
  - None beyond verification commands.
- Exit criteria:
  - All required checks pass locally and in CI.
  - Plan status moved to `Complete`.

## 5) Test Plan

- Unit tests:
  - Run existing module and project unit tests; add targeted tests only where production time handling changed.
- Replay/regression tests:
  - Required only if replay/fusion behavior changes in Phase 4.
- UI/instrumentation tests (if needed):
  - Not required for docs/CI/script-only phases.
- Degraded/failure-mode tests:
  - Validate gate failure output on known violation samples.
- Boundary tests for removed bypasses:
  - Verify production-time APIs outside approved contexts are blocked by static gate.

Required checks:

```bash
python scripts/arch_gate.py
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
| Overly strict gate causes false positives | Dev friction, blocked PRs | Keep explicit exclusions and adapter allowlist policy | XCPro Team |
| Docs updated without matching enforcement | Policy drift | Update docs and gate/workflow in same change set | XCPro Team |
| Dirty worktree mixes unrelated edits | Review noise, accidental regressions | Isolate by phase and validate only intended diffs | XCPro Team |
| Time-call replacements alter behavior | Runtime regressions | Phase 4 targeted tests + replay determinism checks | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry)
- Required command set passes:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## 8) Rollback Plan

- What can be reverted independently:
  - CI workflow file
  - `scripts/arch_gate.py` rule additions
  - Individual doc updates
- Recovery steps if regression is detected:
  - Revert offending phase changes only.
  - Re-run required checks.
  - Re-introduce change with narrowed scope and tests.

## 9) Implementation Evidence (2026-03-01)

Implemented artifacts:

- `.github/workflows/quality-gates.yml`
- `scripts/arch_gate.py`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/README.md`

Clock/time abstraction anchors verified:

- `core/time/src/main/java/com/example/xcpro/core/time/Clock.kt`
- `app/src/main/java/com/example/xcpro/di/TimeModule.kt`

Verification results:

- `python scripts/arch_gate.py` -> PASS
- `./gradlew enforceRules` -> PASS
- `./gradlew :feature:map:testDebugUnitTest -Pxcpro.test.timeout.seconds=120` -> PASS
- `./gradlew testDebugUnitTest -Pxcpro.test.timeout.seconds=120` -> PASS
- `./gradlew assembleDebug` -> PASS
