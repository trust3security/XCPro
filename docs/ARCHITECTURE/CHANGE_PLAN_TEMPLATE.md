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

## 2) Architecture Contract

### 2.1 SSOT Ownership

List each affected data item and one authoritative owner.

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| | | | |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
- Any boundary risk:

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

### 2.5 Replay Determinism

- Deterministic for same input: Yes/No
- Randomness used: Yes/No (if yes, how seeded)
- Replay/live divergence rules:

## 3) Data Flow (Before -> After)

Describe end-to-end flow as text:

```
Source -> Repository (SSOT) -> UseCase -> ViewModel -> UI
```

## 4) Implementation Phases

For each phase, define:

- Goal
- Files to change
- Tests to add/update
- Exit criteria

## 5) Test Plan

- Unit tests:
- Replay/regression tests:
- UI/instrumentation tests (if needed):
- Degraded/failure-mode tests:

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

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry)

## 8) Rollback Plan

- What can be reverted independently:
- Recovery steps if regression is detected:
