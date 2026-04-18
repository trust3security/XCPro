# HOTSPOTS Active/Recent Winner Implementation Plan

This plan follows `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md` and is scoped to
hotspot winner selection inside the existing 700 m dedupe radius.

## 0) Metadata

- Title: OGN Hotspots - Prefer Active/Recent Winner per 700 m Area
- Owner: XCPro map/OGN layer
- Date: 2026-02-27
- Issue/PR: TBD
- Status: Complete (Implemented 2026-02-27)

## 1) Scope

- Problem statement:
Current dedupe keeps one hotspot per ~700 m area, but winner priority is mainly
strength-first. This can keep older/finalized strong climbs visible while
newer active climbs in the same area are hidden.
- Why now:
Pilot expectation is "show what is working now." Current logic can bias toward
historical peaks over tactically relevant active/recent thermals.
- In scope:
repository policy update for local-area winner ordering, unit tests, and docs.
- Out of scope:
retention range changes, display-percent range changes, map overlay redesign,
protocol parsing changes.
- User-visible impact:
within one crowded local area, the displayed hotspot should better represent
active/recent climb opportunity, with deterministic tie-break behavior.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Local-area hotspot winner policy | `OgnThermalRepository` | internal publish policy | UI-side winner filtering |
| Winner priority constants (active/recent/strength tie-break) | `OgnThermalRepository` (or `ogn` domain helper) | internal constants/helpers | ad-hoc comparator logic in overlay/composables |
| Hotspot output list | `OgnThermalRepository` | `StateFlow<List<OgnThermalHotspot>>` | ViewModel/UI-maintained hotspot mirrors |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
`feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt`,
`feature/map/src/test/java/com/trust3/xcpro/ogn/OgnThermalRepositoryTest.kt`,
`docs/HOTSPOTS/*.md`, optionally `docs/ARCHITECTURE/PIPELINE.md`.
- Any boundary risk:
low; change remains repository-owned policy with no new UI/domain dependency leak.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Per-area winner ranking (strength-first only) | `OgnThermalRepository` | `OgnThermalRepository` | Keep policy in one owner but change ranking semantics to active/recent-first | Repository unit tests |
| Active/recent staleness threshold semantics | implicit tie-break behavior | explicit repository constant + comparator | Prevent ambiguity and UI drift | Repository unit tests + docs |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | No known UI/runtime bypass currently computes winners | Keep repository as sole policy owner | Phase 1-2 verification |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| "Recent" freshness check for winner rank | Monotonic (`updatedAtMonoMs`) | Avoid wall-time jumps and keep deterministic comparisons |
| Retention cutoff (`1h..all day`) | Wall + local timezone | Contract already defined as elapsed/local-midnight |
| Thermal state (`ACTIVE`/`FINALIZED`) | Monotonic-driven tracker lifecycle | Derived by repository continuity rules |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
repository publish policy remains on injected `@DefaultDispatcher`.
- Primary cadence/gating sensor:
OGN target emission + existing housekeeping timer.
- Hot-path latency budget:
no worse than current thermal publish path; target <= 5 ms typical snapshots.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
winner freshness uses monotonic sequence from injected clock in tests; replay
must pin test clock progression.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Policy leakage into UI | `ARCHITECTURE.md` SSOT/UDF | review + unit tests | `OgnThermalRepositoryTest` |
| Time-base mixing in freshness rank | `ARCHITECTURE.md` time-base rules | unit tests + review | `OgnThermalRepositoryTest` fake clock cases |
| Non-deterministic winner ties | determinism requirements | unit tests | `OgnThermalRepositoryTest` deterministic tie tests |
| Regression in dedupe semantics | hotspot contract | unit tests | `OgnThermalRepositoryTest` area winner tests |

## 3) Data Flow (Before -> After)

Before:

```text
hotspots -> sort by strength -> distance dedupe -> top N% -> publish
```

After:

```text
hotspots -> rank by active/recent/strength policy -> distance dedupe winner keep/replace
         -> top N% -> publish
```

Notes:
- Retention and top-share filters remain repository-owned and unchanged in role.
- UI still consumes repository output only.

## 4) Implementation Phases

### Phase 0 - Baseline Lock

- Goal:
lock current behavior and add tests that express desired active/recent winner semantics.
- Files to change:
`OgnThermalRepositoryTest.kt`.
- Tests to add/update:
failing tests for active/recent winner preference within 700 m.
- Exit criteria:
baseline tests clearly capture current gap and expected behavior.

### Phase 1 - Winner Policy Model

- Goal:
add explicit comparator/policy helpers for area winner ranking.
- Files to change:
`OgnThermalRepository.kt` (and optional helper file in `ogn` package).
- Policy order (proposed):
1. Active and recent
2. Finalized and recent
3. Active but stale
4. Finalized and stale
Then tie-break by strength (`maxClimbRate`, `averageBottomToTop`), then
`updatedAtMonoMs`, then ID for deterministic ordering.
- Tests to add/update:
unit tests for each rank tier and tie-break chain.
- Exit criteria:
comparator behavior deterministic and fully unit-tested.

### Phase 2 - Repository Publish Integration

- Goal:
apply policy to local-area dedupe winner selection without changing SSOT owner.
- Files to change:
`OgnThermalRepository.kt`.
- Tests to add/update:
integration-style repository tests where multiple nearby hotspots compete.
- Exit criteria:
within 700 m, winner reflects active/recent-first policy; outside 700 m unaffected.

### Phase 3 - Regression and Contract Hardening

- Goal:
confirm no regression in existing retention/top-share/anti-fake behavior.
- Files to change:
`OgnThermalRepositoryTest.kt`, hotspot docs.
- Tests to add/update:
re-run/extend tests for:
`>730` turn gate, retention 1h/all-day, top-share filtering, hotspot ID recovery.
- Exit criteria:
all old contract tests still pass plus new winner tests.

### Phase 4 - Docs and Verification

- Goal:
sync docs and complete required checks.
- Files to change:
`docs/HOTSPOTS/00_INDEX.md`, `docs/HOTSPOTS/02_MODIFICATION_GUIDE.md`,
`docs/HOTSPOTS/03_TESTING_AND_REPASS_CHECKLIST.md`, optionally
`docs/ARCHITECTURE/PIPELINE.md`.
- Tests to add/update:
none beyond Phase 3 unless new branch discovered.
- Exit criteria:
docs match implementation constants/ordering; required Gradle checks pass.

## 5) Test Plan

- Unit tests:
`feature/map/src/test/java/com/trust3/xcpro/ogn/OgnThermalRepositoryTest.kt`
add/update:
  - active-recent beats stronger finalized within 700 m
  - recent finalized beats stale active (if policy adopts recency-first freshness tier)
  - equal rank falls back to strength then recency then ID deterministically
  - outside-radius hotspots are both kept
- Replay/regression tests:
fake-clock deterministic progression for recent/stale transitions.
- UI/instrumentation tests (if needed):
none required for policy-only change unless map behavior discrepancy appears.
- Degraded/failure-mode tests:
invalid coordinates, missing hotspot IDs, quiet-stream housekeeping with same policy.
- Boundary tests for removed bypasses:
assert no UI overlay path reorders thermal winners.

Required checks:

```bat
./gradlew.bat enforceRules
./gradlew.bat :feature:map:testDebugUnitTest
./gradlew.bat assembleDebug
```

Optional when relevant:

```bat
./gradlew.bat connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Over-prioritizing recent weak climbs | Could hide tactically better strong climb | keep strength tie-break high within same rank tier; field-test sample tracks | OGN feature owner |
| Rank ambiguity between active vs recent | Inconsistent pilot expectation | document explicit ordered tiers in code and docs | OGN feature owner |
| Performance drift in crowded scenes | UI jank risk | keep single-pass + bounded comparisons; benchmark with dense fixtures | Map runtime owner |
| Doc/code drift | Support confusion | update HOTSPOTS docs in same PR | OGN feature owner |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved
- Hotspot winner contract met:
within 700 m, displayed winner follows active/recent-first policy with deterministic tie-breaks.

## 8) Rollback Plan

- What can be reverted independently:
winner comparator helper and publish ordering changes.
- Recovery steps if regression is detected:
revert policy commit(s), keep prior strength-first dedupe, retain unrelated retention/top-share safeguards.

## 9) Implementation Notes and Open Decisions

- Proposed recent window constant (initial): `RECENT_WINNER_WINDOW_MS = 300_000` (5 min).
- If flight-test feedback shows excessive churn, increase to 8-10 min with tests/docs update.
- No new user setting is proposed initially; keep policy deterministic and repository-owned.
