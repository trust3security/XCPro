# Map Task Legacy AAT Overlay Deletion

## 0) Metadata

- Title: Delete the isolated legacy AAT interactive overlay stack from `feature:map`
- Owner: XCPro Team
- Date: 2026-03-15
- Issue/PR: TBD
- Status: Complete

## 1) Scope

- Problem statement:
  - `feature:map` still contains an older AAT interaction/overlay stack that is not wired into the live `MapGestureSetup` task gesture path.
- Why now:
  - The prior task-runtime extraction moved the real runtime owners, but this dead cluster still inflates `feature:map` breadth and leaves a second AAT interaction path in source.
- In scope:
  - Delete the isolated legacy AAT interactive manager/map/ui stack and its ownership-aligned test.
  - Keep the live `MapGestureSetup` + `TaskGestureHandler` + `MapOverlayStack` path unchanged.
  - Update `enforceRules` so deleted files no longer carry line-budget obligations and cannot silently reappear.
- Out of scope:
  - Live task authority/runtime changes.
  - New AAT interaction features.
  - Map shell restructuring beyond removing the dead cluster.
- User-visible impact:
  - None intended.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Live AAT map gesture/edit runtime | `MapGestureSetup` + `feature:map-runtime` task gesture handlers | map shell callbacks into coordinator/runtime | duplicate legacy AAT interaction managers/overlays in `feature:map` |
| Task render sync trigger | `TaskMapOverlay` + `MapOverlayStack` live shell path | shell callback into `TaskRenderSyncCoordinator` | second interactive overlay pipeline |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| AAT edit mode / drag state in production | live task gesture path (`MapGestureSetup` -> `TaskGestureHandler`) | map shell callbacks and coordinator-owned mutations | map shell and runtime render path | active task snapshot + live gesture input | coordinator/persistence path | existing edit-mode exit and task reset flows | monotonic gesture/runtime time only where already defined | existing map/task gesture tests |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map`
  - `scripts/ci`
  - `docs/refactor`
- Any boundary risk:
  - Avoid deleting the live shell path (`TaskMapOverlay`, `MapOverlayStack`, `MapGestureSetup`) while removing the dead cluster.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/MapGestureSetup.kt` | current production AAT/task gesture owner | keep one explicit map-shell gesture path | none |
| `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskMapOverlay.kt` | current production task render-sync shell | preserve shell-only task overlay path | none |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| legacy AAT interactive overlay path | isolated files in `feature:map` | deleted | not production-owned; leaves a duplicate interaction path in source | compile + tests |
| CI ownership guard for deleted stack | none | `scripts/ci/enforce_rules.ps1` | prevent legacy reintroduction after deletion | `enforceRules` |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| legacy AAT overlay/manager cluster | second interaction path outside `MapGestureSetup` | remove entirely; keep live shell path only | single phase |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Map_Task_Legacy_AAT_Overlay_Deletion_2026-03-15.md` | New | focused execution record for this cleanup | separate follow-on deletion slice | not part of prior runtime move plan | No |
| `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATInteractiveTurnpointManager.kt` | Existing | delete dead legacy manager | not a live production owner | not worth migrating because it is unused | No |
| `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/AATInteractiveTurnpointIntegration.kt` | Existing | delete dead integration wrapper | only feeds deleted legacy overlay path | not a live shell seam | No |
| `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/map/*.kt` legacy stack files | Existing | delete dead map-side legacy interaction state/drag helpers | second interaction path is no longer allowed | live owner already exists elsewhere | No |
| `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ui/*.kt` legacy stack files | Existing | delete dead legacy overlay UI pieces | only serve the deleted legacy path | not part of the live shell path | No |
| `feature/map/src/test/java/com/trust3/xcpro/tasks/aat/AATInteractiveTurnpointManagerValidationTest.kt` | Existing | delete test for deleted owner | ownership-aligned with removed code | not valid once owner is gone | No |
| `scripts/ci/enforce_rules.ps1` | Existing | enforce absence of deleted legacy stack and remove obsolete line budgets | repository guard owner | not appropriate in app/module code | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| none | n/a | n/a | n/a | this slice deletes dead internal APIs rather than adding new ones | n/a |

### 2.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| none | no new long-lived scope introduced | n/a | n/a | deletion-only slice |

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| none | n/a | no shim retained | n/a | n/a | n/a |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| AAT area tap detection | `feature/tasks/src/main/java/com/trust3/xcpro/tasks/aat/map/AATAreaTapDetector.kt` | live task edit/runtime owners | task-core-owned hit-testing policy already exists there | No |

### 2.2I Stateless Object / Singleton Boundary

| Object / Holder | Why `object` / Singleton Is Needed | Mutable State? | Why It Is Non-Authoritative | Why Not DI-Scoped Instance? | Guardrail / Test |
|---|---|---|---|---|---|
| none | no new singleton-like holder added | n/a | n/a | n/a | n/a |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| none added or changed | n/a | deletion-only slice |

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - No new dispatcher ownership.
- Primary cadence/gating sensor:
  - No cadence change.
- Hot-path latency budget:
  - No hot-path addition; source reduction only.

### 2.4A Logging and Observability Contract

| Boundary / Callsite | Logger Path (`AppLogger` / Platform Edge) | Sensitive Data Risk | Gating / Redaction | Temporary Removal Plan |
|---|---|---|---|---|
| none | n/a | n/a | n/a | n/a |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - No new divergence; dead code removal only.

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| legacy path removed | Unavailable | compile-time source cleanup | none; path was not live | none | compile + existing live-path tests |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| none | n/a | n/a | n/a | deletion-only slice |

### 2.5C No-Op / Test Wiring Contract

| Class / Boundary | NoOp / Convenience Path | Production Allowed? | Safe Degraded Behavior | Visibility / Guardrail |
|---|---|---|---|---|
| none | n/a | n/a | n/a | n/a |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| deleted legacy stack silently returns in `feature:map` | no duplicate owner paths | `enforceRules` | `scripts/ci/enforce_rules.ps1` |
| deletion accidentally removes live task shell path | preserve explicit shell/runtime split | compile + repo unit tests | Gradle compile/tests |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| no change to live task/AAT map interaction | MAP-TASK-SHELL-01 | current live gesture path | unchanged | compile + existing map/task tests | single phase |

## 3) Data Flow (Before -> After)

Before:

`feature:map` live task shell path + dead legacy AAT overlay path both exist in source`

After:

`feature:map` live task shell path only -> `feature:map-runtime` task gesture runtime -> coordinator/runtime mutations`

## 4) Implementation Phases

### Phase 1

- Goal:
  - Delete the isolated legacy AAT manager/map/ui stack and its ownership-aligned test.
- Outcome:
  - Completed 2026-03-15. Deleted the legacy AAT interaction manager/integration, map interaction state/drag helpers, overlay UI pieces, and the dead validation test.
- Files to change:
  - legacy `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/**` cluster
  - legacy `feature/map/src/test/java/com/trust3/xcpro/tasks/aat/AATInteractiveTurnpointManagerValidationTest.kt`
- Ownership/file split changes in this phase:
  - live map shell path remains
  - dead legacy path is removed
- Tests to add/update:
  - none new; remove deleted-owner test
- Exit criteria:
  - deleted stack has no remaining production/test references
  - `feature:map` still compiles through the live shell path

### Phase 2

- Goal:
  - Update `enforceRules` and verify the cleanup.
- Outcome:
  - Completed 2026-03-15. Added an absence guard for the removed legacy stack, removed obsolete line-budget checks, and passed the required Gradle gates.
- Files to change:
  - `scripts/ci/enforce_rules.ps1`
- Ownership/file split changes in this phase:
  - CI becomes the guard against legacy path reintroduction
- Tests to add/update:
  - none; rule coverage only
- Exit criteria:
  - required Gradle gates pass

## 5) Test Plan

- Unit tests:
  - existing map/task tests
- Replay/regression tests:
  - none needed; no replay behavior change
- UI/instrumentation tests (if needed):
  - not required for dead-code deletion slice
- Degraded/failure-mode tests:
  - compile and rule verification only
- Boundary tests for removed bypasses:
  - `enforceRules` guard
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Ownership move / bypass removal / API boundary | Boundary lock tests | `enforceRules` guard + compile |
| UI interaction / lifecycle | UI or instrumentation coverage | existing live-path tests remain green |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| accidentally deleting live AAT shell code | map task interaction regression | delete only files proven isolated by seam pass and re-run full gates | XCPro Team |
| rule script still requires removed files | false red CI | remove obsolete line-budget entries in same slice | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file:
- Decision summary:
  - This slice removes dead internal code and does not introduce a new durable boundary or contract.
- Why this belongs in an ADR instead of plan notes:
  - n/a

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Replay behavior remains deterministic
- `KNOWN_DEVIATIONS.md` unchanged

## 8) Rollback Plan

- What can be reverted independently:
  - the deletion slice and its rule updates can be reverted together without touching the live gesture/runtime path
- Recovery steps if regression is detected:
  - restore the deleted files from version control and remove the new absence guard
