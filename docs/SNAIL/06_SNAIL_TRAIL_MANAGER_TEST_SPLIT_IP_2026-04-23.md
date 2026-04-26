# Implementation Plan: SnailTrailManager Test Split

## 0) Metadata

- Title: SnailTrailManager test split
- Owner: XCPro Team
- Date: 2026-04-23
- Issue/PR: TBD
- Status: Complete
- Later 2026-04-23 note: this test split was superseded by the
  raw-body/display-tail ownership correction. `SnailTrailDisplayStoreTest.kt`
  was removed with the duplicate display-pose trail-body store.

## 1) Scope

- Problem statement: `SnailTrailManagerTest.kt` is close to the preferred
  450-line target after the display-pose trail work.
- Why now: the commit hook reported the file as approaching the preferred
  limit, and `AGENTS.md` says to split code by responsibility before files
  become large when practical.
- In scope:
  - Move display-pose manager policy tests from `SnailTrailManagerTest.kt` to
    the existing `SnailTrailManagerDisplayPoseTest.kt`.
  - Keep test assertions and manager setup behavior equivalent.
  - Run focused manager trail tests after the move.
- Out of scope:
  - Production code changes.
  - New runtime seams, state owners, feature flags, or DI wiring.
  - Shared fixture extraction unless Phase 1 fails the line-budget goal.
  - README/global architecture doc updates.
- User-visible impact: none.
- Rule class touched: Default, file ownership and line-budget hygiene.

## 1A) Confirmed Boundaries / Verified Facts

| Fact | Source of Truth | Why It Matters |
|---|---|---|
| Kotlin files should target `<= 450` lines when practical and must stay under the enforced `<= 500` default. | `AGENTS.md`; `docs/ARCHITECTURE/CODING_RULES.md` section `1A.4` | The change should reduce file-size pressure without creating a larger refactor. |
| `SnailTrailManagerTest.kt` currently has 457 physical lines by `Get-Content(...).Count`. | Local file inspection on 2026-04-23 | The file is above the preferred target but below the hard cap. |
| `SnailTrailManagerDisplayPoseTest.kt` already exists and has 129 physical lines by `Get-Content(...).Count`. | Local file inspection on 2026-04-23 | It is the natural target for display-pose manager tests and has ample room. |
| `SnailTrailManagerTest.kt` currently mixes raw trail visibility/tail policy tests with display-pose trail policy tests. | Local inspection of `feature/map-runtime/src/test/java/com/trust3/xcpro/map/trail/SnailTrailManagerTest.kt` | Splitting by tested responsibility is more stable than splitting by arbitrary line count. |
| `SnailTrailManagerDisplayPoseTest.kt` already owns connector and replay/live display-pose manager tests. | Local inspection of `feature/map-runtime/src/test/java/com/trust3/xcpro/map/trail/SnailTrailManagerDisplayPoseTest.kt` | Moving display-pose policy tests there preserves existing test ownership. |
| This plan is test-only and does not change map runtime behavior. | Requested scope and affected file set | No `PIPELINE.md`, ADR, visual SLO, replay, or Levo doc sync is required for the split itself. |

## 1B) Explicit Decisions / Defaults Chosen

| Decision / Default | Source of Decision | Impact If Wrong | Follow-up / Owner |
|---|---|---|---|
| Do not change production code in this cleanup. | User request: "no adhoc / no churn"; verified issue is test-file size only | Could hide behavior changes inside a cleanup | XCPro Team |
| Move only the remaining display-pose manager tests. | Existing test ownership in `SnailTrailManagerDisplayPoseTest.kt` | Moving unrelated tests would create review churn | XCPro Team |
| Do not extract a shared test fixture in Phase 1. | Minimal-change rule from `CODING_RULES.md`; duplicated helpers are local and bounded | A fixture could create a new utility seam and larger diff | Reassess only if Phase 1 does not satisfy the line target |

## 1C) Unresolved Decisions / Questions

None for this documentation-only plan.

Implementation completed on 2026-04-23.

## 2) Architecture Contract

### 2.1 SSOT Ownership

No app data, runtime state, or SSOT ownership changes.

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Trail runtime/display behavior | Existing production trail owners | Existing production APIs | Do not introduce new production mirrors or test-only production seams |
| Test grouping | Test files under `feature/map-runtime/src/test/.../trail` | JUnit classes | Do not split tests by arbitrary line count when behavior ownership is clearer |

### 2.1A State Contract

No new or changed state.

### 2.2 Dependency Direction

Dependency flow is unchanged.

- Modules/files touched: test sources only in `feature:map-runtime`.
- Boundary risk: low. Tests may instantiate `SnailTrailManager` with mocks as
  they do today, but no new production dependencies are introduced.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `SnailTrailManagerDisplayPoseTest.kt` | Existing focused manager tests for display-pose trail/connector behavior | Keep display-pose policy tests together | None |
| `SnailTrailDisplayStoreTest.kt` | Removed by the later raw-body/display-tail ownership correction | No current display-store test owner remains | Supersedes this row |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Display-pose manager policy test ownership | `SnailTrailManagerTest.kt` | `SnailTrailManagerDisplayPoseTest.kt` | Existing display-pose test file already owns this behavior area | Focused manager tests pass |

### 2.2C Bypass Removal Plan

None. No bypasses are introduced or removed.

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/map-runtime/src/test/java/com/trust3/xcpro/map/trail/SnailTrailManagerTest.kt` | Existing | Raw trail hidden policy, raw tail clearing policy, zoom/full manager behavior | Keeps non-display-pose manager policy tests together | Moving these would create unnecessary churn | Yes, remove display-pose tests only |
| `feature/map-runtime/src/test/java/com/trust3/xcpro/map/trail/SnailTrailManagerDisplayPoseTest.kt` | Existing | Display-pose manager policy, connector behavior, replay/live display-pose cadence assertions | Existing focused owner for display-pose manager tests | New file would duplicate an existing owner | No new file needed |

### 2.2E Module and API Surface

No new or changed public/cross-module API.

### 2.2F Scope Ownership and Lifetime

No coroutine scope or lifetime changes.

### 2.2G Compatibility Shim Inventory

No shims or bridges.

### 2.2H Canonical Formula / Policy Owner

No formula, constant, cadence, or production policy changes.

### 2.2I Stateless Object / Singleton Boundary

No new `object`, singleton-like holder, or convenience constructor.

### 2.3 Time Base

No production time-base changes.

The moved tests may continue to use existing deterministic literal timestamps
for live/replay display-pose assertions.

### 2.4 Threading and Cadence

No dispatcher, threading, or cadence changes.

### 2.4A Logging and Observability Contract

No logging changes.

### 2.5 Replay Determinism

- Deterministic for same input: unchanged.
- Randomness used: no.
- Replay/live divergence rules: unchanged.

### 2.5A Error and Degraded-State Contract

No error or degraded-state behavior changes.

### 2.5B Identity and Model Creation Strategy

No IDs or timestamps are created in production code.

### 2.5C No-Op / Test Wiring Contract

No new production `NoOp` or convenience path.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Test file remains above preferred target | `AGENTS.md`; `CODING_RULES.md` section `1A.4` | File-size hook / `enforceRules` | `SnailTrailManagerTest.kt` |
| Behavior changes while moving tests | `CODING_RULES.md` section `15A` | Focused unit tests | `SnailTrailManagerTest`, `SnailTrailManagerDisplayPoseTest` |
| Cleanup grows into production refactor | `PLAN_MODE_START_HERE.md` smallest safe workflow | Review | This IP and final diff |

### 2.7 Visual UX SLO Contract

Not applicable. This is a test-only split with no map rendering behavior change.

## 3) Data Flow

Before and after data flow are identical:

```text
Existing production trail runtime -> existing SnailTrailManager behavior -> tests assert calls against mocked SnailTrailOverlay
```

## 4) Implementation Phases

### Phase 0: Plan Only

- Goal: record the narrow split and boundaries before editing tests.
- Files to change: this IP only.
- Exit criteria: plan exists and names exact in/out scope.

### Phase 1: Move Display-Pose Policy Tests

- Goal: reduce `SnailTrailManagerTest.kt` below the preferred target by moving
  only display-pose manager policy tests.
- Files to change:
  - `SnailTrailManagerTest.kt`
  - `SnailTrailManagerDisplayPoseTest.kt`
- Tests to move:
  - `updateDisplayPose_liveRendersDisplayTrailWhenFlagEnabled`
  - `updateDisplayPose_replayRendersDisplayTrailWhenFlagEnabled`
  - `updateDisplayPose_liveClearsDisplayTrailWhenFlagDisabled`
- Exit criteria:
  - `SnailTrailManagerTest.kt` is below 450 physical lines.
  - `SnailTrailManagerDisplayPoseTest.kt` remains below 450 physical lines.
  - No production files changed.

### Phase 2: Focused Verification

- Goal: prove behavior survived the test move.
- Command:

```powershell
.\gradlew.bat :feature:map-runtime:testDebugUnitTest --tests com.trust3.xcpro.map.trail.SnailTrailManagerTest --tests com.trust3.xcpro.map.trail.SnailTrailManagerDisplayPoseTest
```

- Exit criteria: command passes.

### Phase 3: Optional Gate

- Goal: run broader checks only if review requires them or if the test move
  unexpectedly touches non-test behavior.
- Commands:

```powershell
.\gradlew.bat enforceRules
```

- Exit criteria: command passes if run.

## 5) Test Plan

- Unit tests: focused `SnailTrailManagerTest` and
  `SnailTrailManagerDisplayPoseTest`.
- Replay/regression tests: not required for a test-only move; existing replay
  display-pose assertions remain in the display-pose test file.
- UI/instrumentation tests: not required.
- Degraded/failure-mode tests: not required.
- Boundary tests for removed bypasses: not applicable.

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests if behavior changes | Not applicable, behavior unchanged |
| Time-base / replay / cadence | Existing deterministic tests remain passing | Focused manager tests |
| Persistence / settings / restore | Round-trip / restore / migration tests | Not applicable |
| Ownership move / bypass removal / API boundary | Boundary lock tests | Not applicable; test-file ownership only |
| UI interaction / lifecycle | UI or instrumentation coverage | Not applicable |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | Not applicable |

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Fixture extraction creates more churn than the line split | Larger review and possible helper ownership debate | Do not extract fixtures in Phase 1 | XCPro Team |
| Moved tests accidentally change assertions | False confidence or behavior coverage loss | Move assertions intact; run focused tests | XCPro Team |
| Display-pose test file grows too large later | Same line-budget issue shifts files | Reassess with a separate plan if it approaches 450 | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No.
- ADR file: None.
- Decision summary: This is a test-file ownership cleanup, not a production
  architecture decision.
- Why this belongs in an IP instead of an ADR: the plan guards against ad hoc
  cleanup and churn, but it does not define a durable runtime boundary.

## 7) Acceptance Gates

- No production files changed.
- No new shared test fixture in Phase 1.
- `SnailTrailManagerTest.kt` below 450 physical lines.
- Display-pose manager tests live in `SnailTrailManagerDisplayPoseTest.kt`.
- Focused manager test command passes.
- `KNOWN_DEVIATIONS.md` unchanged.

## 8) Rollback Plan

- Revert the test-method move only.
- Since production code is untouched, rollback is limited to test source files.
