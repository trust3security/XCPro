
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

---

# 7) Ready-To-Run Contract: Map/Task 5 of 5 Hardening (2026-02-12)

Use this section when the task is "raise map/task quality to 5/5 with no
behavior regressions."

## 7.1 Execution Prompt (Copy/Paste)

```text
Use `docs/ADS-b/Agent-Execution-Contract.md` Section 7 as the active contract.
Implement autonomously end-to-end.

Primary plan doc:
`docs/refactor/Map_Task_5of5_Stabilization_Plan_2026-02-12.md`

Non-negotiables:
- Preserve MVVM + UDF + SSOT boundaries.
- Remove map/task UI bypasses called out in the plan.
- Keep replay deterministic.
- No partial TODO-only refactor.
```

## 7.2 Fixed Change Request

- Consolidate map/task rendering to a single runtime owner.
- Remove direct task manager/service/replay manager access from map Composables.
- Replace UI-side direct mutation/query bypasses with ViewModel intents + state.
- Re-enable and expand tests for map/task high-risk paths.
- Decompose map/task orchestration hotspots to lower blast radius.

## 7.3 In-Scope Files (Initial)

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapGestureSetup.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapTaskIntegration.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/ui/task/MapTaskScreenUiTest.kt`

## 7.4 Ordered Phases (Must Follow)

1. Phase 0: Baseline + guardrails.
2. Phase 1: Single task render owner.
3. Phase 2: UI boundary hardening.
4. Phase 3: Maintainability decomposition.
5. Phase 4: Test confidence closure.
6. Phase 5: Docs + full verification.

For each phase, agent output must include:
- Files changed.
- Tests added/updated.
- Verification commands + pass/fail.
- Any assumptions taken.

## 7.5 Hard Acceptance Criteria

- Exactly one render ownership path performs task clear+plot orchestration.
- Map Composables no longer directly mutate/query task coordinator for business
  behavior (intent/state path only).
- `MapScreenRoot` no longer acquires task/service/replay managers via app
  entry-point bypasses.
- Ignored tests in map/task critical path are zero.
- Required checks all pass:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
  - `./gradlew connectedDebugAndroidTest` (when emulator/device is available)

## 7.6 5/5 Exit Score Rules

- Overall quality 5/5:
  - All acceptance criteria met and no open P0/P1 findings.
- Release readiness 5/5:
  - All required checks green, including connected tests when environment allows.
- Architecture cleanliness 5/5:
  - All bypass-removal items in the plan are closed.
- Maintainability/change safety 5/5:
  - Hotspot decomposition gates are met and render path ownership is singular.
- Test confidence 5/5:
  - Deterministic reruns stable and no ignored tests in risky map/task scope.

## 7.7 Execution Evidence (2026-02-12)

Latest autonomous execution status against Section 7:

- Completed:
  - Single task-render ownership via `TaskMapRenderRouter.syncTaskVisuals(...)`.
  - Map root bypass removal for task/service/replay manager access.
  - Non-root `EntryPointAccessors.fromApplication(...)` runtime callsites removed (now 0).
  - Task gesture/edit paths routed through use-case + ViewModel APIs.
  - Critical map/task ignored-test debt removed (`MapScreenViewModelTest`, `MapTaskScreenUiTest`).
  - Hotspot decomposition gates met:
    - `MapScreenRoot.kt` 392 LOC
    - `MapScreenContent.kt` 337 LOC
    - `MapScreenViewModel.kt` 418 LOC
    - `MapScreenScaffoldInputs.kt` 304 LOC

- Verification results:
  - `./gradlew enforceRules` -> PASS
  - `./gradlew testDebugUnitTest` -> PASS
  - `./gradlew assembleDebug` -> PASS
  - `./gradlew connectedDebugAndroidTest --no-parallel` -> PASS on `SM-S908E - Android 16`

- Outstanding external gate:
  - None.

## 7.8 Follow-On Autonomous Contract: ROI Hardening Pass

Use this when executing the post-closure cleanup defined in:
- `docs/refactor/Map_Task_5of5_Stabilization_Plan_2026-02-12.md` (Section 9)

Execution order:
1. Workstream A: consolidate task render sync triggers to one owner path.
2. Workstream B: decompose `MapInitializer` and `TaskManagerCoordinator`.
3. Workstream C: remove remaining runtime `EntryPoints.get(...)` lookups.

Hard requirements:
- No behavior regressions in task overlay toggle/edit/navigation flows.
- Preserve MVVM + UDF + SSOT boundaries.
- Keep replay deterministic.
- Keep docs in sync with final wiring.

Acceptance criteria for the pass:
- Trigger ownership:
  - Direct production callsites of `TaskMapRenderRouter.syncTaskVisuals(...)` are reduced to a single coordinator owner.
- Maintainability:
  - `MapInitializer` and `TaskManagerCoordinator` responsibilities are split into bounded collaborators.
- Dependency purity:
  - Runtime UI/composable code no longer uses `EntryPoints.get(...)`.
- Verification:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
  - `./gradlew connectedDebugAndroidTest --no-parallel` (device/emulator available)

Execution status update (2026-02-12):
- Workstream A: Completed.
  - Added `TaskRenderSyncCoordinator` as the single runtime trigger owner.
  - Rewired map/task trigger callsites to coordinator methods.
  - Added `TaskRenderSyncCoordinatorTest` for dedupe + pending-map behavior.
- Workstream B: Completed.
  - `MapInitializer` split with dedicated collaborators:
    - `MapScaleBarController`
    - `MapInitializerDataLoader`
  - `TaskManagerCoordinator` persistence/sync responsibilities extracted to:
    - `TaskCoordinatorPersistenceBridge`
- Workstream C: Completed.
  - Removed all runtime entrypoint lookups (`EntryPoints.get(...)` now 0).
  - Replaced entrypoint acquisition with Hilt ViewModel hosts:
    - `TaskManagerCoordinatorHostViewModel`
    - `TaskScreenUseCasesViewModel`
  - Removed obsolete `MapUseCaseEntryPoint`.
- Verification rerun after B/C:
  - `./gradlew enforceRules` -> PASS
  - `./gradlew testDebugUnitTest` -> PASS
  - `./gradlew assembleDebug` -> PASS
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel` -> PASS on `SM-S908E - Android 16`

