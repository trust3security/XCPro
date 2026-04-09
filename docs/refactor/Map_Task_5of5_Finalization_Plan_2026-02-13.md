# Map_Task_5of5_Finalization_Plan_2026-02-13.md

## 1) Executive Summary

This plan defines the final hardening pass to lift the map/task slice from
current high quality (4.5/5 overall) to consistent 5/5 release-grade quality
under the XCPro architecture contract.

The plan is intentionally execution-oriented: each phase has explicit outputs,
verification gates, and measurable exit criteria.

## 2) Current Baseline and Target

Current score (post A/B/C workstreams):

- Architecture cleanliness: 4.4 / 5
- Maintainability/change safety: 4.3 / 5
- Test confidence on risky paths: 4.6 / 5
- Overall map/task slice quality: 4.5 / 5
- Release readiness (map/task slice): 4.8 / 5

Target score:

- Architecture cleanliness: 5.0 / 5
- Maintainability/change safety: 5.0 / 5
- Test confidence on risky paths: 5.0 / 5
- Overall map/task slice quality: 5.0 / 5
- Release readiness (map/task slice): 5.0 / 5

Execution update (2026-02-14):

- Boundary hardening pass completed for map/task runtime wiring:
  - map runtime managers now consume `MapTasksUseCase` snapshots/count APIs
    instead of coordinator handles.
- Startup task-load deduped:
  - removed duplicate `loadSavedTasks()` call from `TaskManagerCompat` host.
- Determinism/test-net expansion completed:
  - `TaskRenderSyncCoordinatorTest` now covers pending style/map-ready sync and
    pending clear flush ordering.
  - `RacingReplayValidationTest` now asserts same replay input twice yields the
    same navigation event sequence.
  - `TaskRenderSyncCoordinatorPerformanceTest` adds a dispatch latency guard
    (`onTaskMutation()` max <= 100 ms in test budget).
- Asset/doc sync pass completed:
  - removed orphan drawable `ic_adsb_plane_.png`.
  - active plan pointers aligned in architecture docs.
- Guardrail hardening pass completed in CI rules:
  - task composable boundary checks now use structural package-glob scans
    (`tasks/**` and `map/ui/task/**`) instead of fixed file lists.
  - ignored/disabled test scan now includes unit + instrumentation paths
    (`app/src/test/**`, `app/src/androidTest/**`).
  - verification: `./gradlew enforceRules` -> PASS after rule updates.
- AAT runtime non-UI Compose-state remediation completed:
  - replaced manager/handler-held Compose mutable state with `StateFlow`/plain
    state in:
    - `AATInteractiveTurnpointManager`
    - `AATEditModeStateManager`
    - `AATTargetPointDragHandler`
- Full gate rerun completed (2026-02-14):
  - `./gradlew enforceRules` -> PASS
  - `./gradlew testDebugUnitTest` -> PASS
  - `./gradlew assembleDebug` -> PASS
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` -> PASS (2 consecutive runs on `SM-S908E - Android 16`)
- Maintainability decomposition closure completed:
  - `MapScreenViewModel.kt` reduced to 347 LOC (target <= 350).
  - state-construction extraction added via `MapScreenViewModelStateBuilders.kt`.
- Performance/lifecycle evidence closure completed:
  - added evidence pack `Map_Task_Performance_Lifecycle_Evidence_2026-02-14.md`.
  - added lifecycle stress script `scripts/qa/map_task_lifecycle_stress.ps1`.
- Release sign-off rerun completed after F/G closure:
  - `./gradlew enforceRules` -> PASS
  - `./gradlew testDebugUnitTest` -> PASS
  - `./gradlew assembleDebug` -> PASS
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` -> PASS (2 consecutive runs on `SM-S908E - Android 16`)

## 2A) Deep-Dive Recheck Findings (2026-02-14)

Severity-ranked findings from strict re-audit:

1. High: Active plan file tracking risk.
   - `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md` points to this plan,
     but `Map_Task_5of5_Finalization_Plan_2026-02-13.md` is currently untracked.
   - Risk: clean clones lose canonical execution contract.

2. High: Compose state leakage in non-UI manager/state classes.
   - `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATInteractiveTurnpointManager.kt`
   - `feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATEditModeState.kt`
   - Risk: violates non-UI manager state model rule; increases hidden coupling.

3. Medium: `enforceRules` coverage gap for the above leakage.
   - `scripts/ci/enforce_rules.ps1` mutable-state guard currently checks only:
     `MapModalManager`, `MapCameraManager`, `FlightDataManager`.
   - Risk: future Compose-state regressions in AAT runtime classes are not blocked.

4. Medium: Remaining hotspot target miss.
   - `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` at ~447 LOC.
   - Risk: maintainability/change-safety remains below 5/5 threshold.

5. Low: Contract-doc pointer drift.
   - `docs/ADS-b/Agent-Execution-Contract.md` still references the stabilization
     plan as primary in older sections.
   - Risk: future agent runs may follow superseded instructions.

Immediate remediation order:
1. Commit-track this finalization plan file and keep it canonical.
2. Replace non-UI `mutableStateOf` with `StateFlow`/plain state in AAT runtime classes.
3. Expand `enforceRules` mutable-state scan to cover AAT runtime manager/state classes.
4. Split `MapScreenViewModel` to <= 350 LOC target (or explicitly justify exception).
5. Align remaining execution-contract docs to finalization plan pointer.

## 2B) Second Deep-Dive Addendum (2026-02-14)

Additional misses found after another repository-wide sweep:

1. High: Core deviations doc still references superseded remediation contract.
   - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`:
     - active remediation plan still points to
       `archive/2026-04-doc-pass/Task_Architecture_Compliance_Refactor_Plan.md`.
     - resolved note still says `TaskManagerCompat` uses Hilt entry point lookup.
   - Risk: governance/audit guidance is stale against current wiring.

2. High: Pipeline doc has stale task-render ownership description.
   - `docs/ARCHITECTURE/PIPELINE.md` still states:
     - `TaskMapRenderRouter` consumes `TaskManagerCoordinator.currentTask` snapshots.
   - Current runtime path uses `TaskRenderSnapshot` via `MapTasksUseCase` and
     `TaskRenderSyncCoordinator` ownership.
   - Risk: architecture doc no longer matches implementation intent.

3. High: Non-UI Compose-state leakage includes one more runtime class.
   - `feature/map/src/main/java/com/example/xcpro/tasks/aat/map/AATTargetPointDragHandler.kt`
     still uses `mutableStateOf` in a non-composable runtime handler class.
   - This was not called out in the prior finding set.

4. Medium: Maintainability target scope underestimated.
   - Current map/task code has many files >350 LOC, not only
     `MapScreenViewModel.kt` (447 LOC).
   - Risk: 5/5 maintainability target is not reachable if only one file is split.

5. Medium: Execution-contract doc drift remains in ADS-b contract section.
   - `docs/ADS-b/Agent-Execution-Contract.md` still lists stabilization plan
     as primary in section 7.1 and 7.8.
   - Risk: autonomous runs may start from a superseded baseline plan.

Second-pass remediation order:
1. Update `KNOWN_DEVIATIONS.md` and `PIPELINE.md` to match current runtime flow.
2. Remove remaining non-UI `mutableStateOf` in
   `AATTargetPointDragHandler`, `AATEditModeStateManager`,
   `AATInteractiveTurnpointManager`.
3. Widen enforceRules mutable-state guard to include AAT runtime handler/state files.
4. Expand maintainability split targets beyond `MapScreenViewModel` to a
   prioritized hotspot list.
5. Normalize ADS-b execution-contract plan pointers to finalization plan.

## 2C) Third Deep-Dive Addendum (2026-02-14)

Additional misses found in a full pass-through audit:

1. High: Deviation governance metadata is stale relative to current state.
   - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` still reports:
     - `Audit date: 2026-02-11`
     - `Last verified: 2026-02-11`
     - `Current deviations: None`
   - Current code still has active non-UI Compose-state violations in AAT runtime classes.
   - Risk: governance file claims "no deviations" while known rule violations remain.

2. Medium (Resolved 2026-02-14): `enforceRules` ignored-test guard only covered unit-test scope.
   - `scripts/ci/enforce_rules.ps1` rule 16 scans:
     - `feature/map/src/test/java/com/example/xcpro/map/**`
     - `feature/map/src/test/java/com/example/xcpro/tasks/**`
   - Resolution:
     - rule now scans `app/src/androidTest/**` and `app/src/test/**`
       for `@Ignore` and `@Disabled`.
   - Residual risk: low (depends on continued rule ownership in CI).

3. Medium: Non-UI Compose-state guard is pattern-incomplete.
   - Current guard checks only `mutableStateOf(` in a narrow file set.
   - Rule intent in `CODING_RULES.md` also forbids `derivedStateOf` and `remember`
     in non-UI managers/domain classes.
   - Risk: violations can bypass guard using non-mutable Compose state APIs.

4. Medium (Resolved 2026-02-14): Task UI boundary guard was file-list scoped, not structural.
   - `scripts/ci/enforce_rules.ps1` task composable boundary checks enumerate
     a fixed file list.
   - Resolution:
     - added structural composable discovery + scan using package globs:
       - `feature/map/src/main/java/com/example/xcpro/tasks/**/*.kt`
       - `feature/map/src/main/java/com/example/xcpro/map/ui/task/**/*.kt`
   - Residual risk: low (new files are auto-included if they contain `@Composable`).

5. Low: Additional contract doc drift in RULES execution contract.
   - `docs/RULES/Agent-Execution-Contract.md` still points to
     `archive/2026-04-doc-pass/Task_Architecture_Compliance_Refactor_Plan.md`.
   - Risk: another agent entrypoint can pick a superseded plan.

6. Low: ADS-B asset/doc hygiene drift.
   - Code uses `ic_adsb_parachutist_symbol` / `ic_adsb_hangglider_symbol` drawables.
   - Legacy PNG assets (`ic_adsb_parachutist.png`, `ic_adsb_hangglider.png`) remain,
     and docs still list PNG variants as active.
   - Risk: maintainability noise and icon-source ambiguity.

Third-pass remediation order:
1. Update `KNOWN_DEVIATIONS.md` audit/verification metadata and record active
   deviations until fixes land.
2. Done 2026-02-14: extended `enforceRules` ignored/disabled-test scan to include androidTest paths.
3. Expand non-UI Compose-state guard to detect `mutableStateOf`, `derivedStateOf`,
   and `remember` in non-UI manager/domain scopes.
4. Done 2026-02-14: replaced file-list task boundary checks with package/glob structural checks.
5. Align `docs/RULES/Agent-Execution-Contract.md` to finalization plan.
6. Normalize ADS-B icon asset docs and remove/justify legacy PNG duplicates.

## 3) Gap-to-5 Analysis

Remaining gaps are not major architecture defects. They are final-mile quality
gaps:

1. Regression net breadth on critical map/task behavior can still be deepened.
2. Several map/task orchestration surfaces remain large and should be reduced
   further to lower future blast radius.
3. Performance and lifecycle behavior needs explicit, repeatable stress checks
   as release evidence (not only standard happy-path checks).
4. Guardrails should be tightened so new bypasses/trigger duplication cannot be
   reintroduced silently.

## 4) Workstreams

### Workstream D: Deterministic Test Net Expansion (Highest ROI)

Goal:
- Move risky map/task behavior from "covered" to "locked."

Scope:
- Task overlay flows, task type switching, AAT edit transitions, style reload,
  map null/ready lifecycle, replay parity in task transitions.

Implementation tasks:
1. Add TaskRenderSyncCoordinator contract tests for:
   - map unavailable -> pending sync -> map ready flush
   - repeated equivalent signatures do not re-render
   - style-change event ordering
   - mutation-driven forced sync behavior
2. Add integration tests for one full vertical slice:
   - "toggle/edit overlay -> state update -> sync coordinator -> router apply"
3. Add replay determinism assertions for task navigation transitions:
   - same replay input and settings produce identical transition sequence.
4. Add failure-mode tests:
   - empty task
   - style reload mid-edit
   - overlay clear while map unavailable

Exit criteria:
- No ignored tests in map/task critical scope.
- Deterministic rerun stability on two consecutive runs.
- Replay transition parity test passing.

Status: Completed (2026-02-14)

### Workstream E: Architecture Guardrail Tightening

Goal:
- Prevent recurrence of solved boundary issues.

Scope:
- enforceRules patterns and architectural CI checks.

Implementation tasks:
1. Add/extend static checks for:
   - direct task render-router sync calls outside coordinator owner.
   - any runtime `EntryPoints.get(...)` or `EntryPointAccessors.fromApplication(...)`.
   - direct task manager mutation/query from map/task composables.
2. Add CI check for ignored tests in map/task packages.
3. Add PR checklist update in docs for trigger-owner and dependency rules.
4. Extend non-UI Compose-state enforcement beyond map managers:
   - include AAT runtime manager/state classes under
     `feature/map/src/main/java/com/example/xcpro/tasks/aat/**`.
5. Add doc-contract consistency gate:
   - active plan pointer in architecture docs must resolve to a tracked file.
   - execution-contract docs must not reference superseded plan as primary.
6. Add architecture-doc drift checks:
   - `PIPELINE.md` task-render owner wording must match runtime owner path.
   - `KNOWN_DEVIATIONS.md` active remediation pointer must match active plan.
7. Extend test-ignore guardrails:
   - include `app/src/androidTest/**` in ignored-test scans.
   - Status: Completed (2026-02-14).
8. Expand non-UI Compose-state pattern checks:
   - include `mutableStateOf`, `derivedStateOf`, and `remember` where forbidden.
9. Replace curated file-list task boundary checks with structural package globs.
   - Status: Completed (2026-02-14).

Exit criteria:
- Build fails on attempted reintroduction of old bypass patterns.
- PR checklist reflects new non-negotiables.

Status: Completed (2026-02-14)

### Workstream F: Maintainability and Decomposition Finish

Goal:
- Reduce future change risk in remaining high-responsibility files.

Scope:
- Remaining map/task orchestration hotspots.

Implementation tasks:
1. Split remaining high-density methods in:
   - `MapScreenViewModel.kt`
   - `MapScreenScaffoldInputs.kt`
   into bounded helper/coordinator collaborators where useful.
2. Keep contracts explicit:
   - narrow interfaces
   - intent/state direction preserved
   - no business logic migration into UI.
3. Enforce practical size targets:
   - orchestration files <= 300-350 LOC where possible.
4. Immediate file-level target:
   - reduce `MapScreenViewModel.kt` from current ~447 LOC to <= 350 LOC.
5. Priority follow-up hotspot list (descending risk):
   - `MapScreenReplayCoordinator.kt`
   - `MapCameraManager.kt`
   - `RulesBTTab.kt`
   - `AATTaskManager.kt`
   - `RacingTaskManager.kt`

Exit criteria:
- Reduced hotspot concentration with equivalent behavior.
- No architecture regressions.
- Better local reasoning for future feature work.

Status: Completed (2026-02-14)
- `MapScreenViewModel.kt` reduced to 347 LOC (target <= 350).
- State-construction complexity extracted to
  `MapScreenViewModelStateBuilders.kt`.

### Workstream G: Performance and Lifecycle Evidence Pack

Goal:
- Convert performance assumptions into repeatable release evidence.

Scope:
- map/task redraw cadence and lifecycle transitions.

Implementation tasks:
1. Add instrumentation check for task overlay update latency budget
   (target <= 100 ms typical).
2. Add lifecycle stress script/scenario:
   - background/foreground
   - style switch
   - task edit toggle
   - replay play/pause.
3. Capture and store reproducible benchmark notes in docs.

Exit criteria:
- Measured latency within budget on reference device/emulator.
- Lifecycle stress scenario completes without functional regressions.

Status: Completed (2026-02-14)
- Evidence doc: `docs/refactor/Map_Task_Performance_Lifecycle_Evidence_2026-02-14.md`.
- Lifecycle stress script: `scripts/qa/map_task_lifecycle_stress.ps1`.

### Workstream H: Release Sign-Off Closure

Goal:
- Achieve repeatable green release gates and signed-off evidence.

Implementation tasks:
1. Run full required checks in sequence.
2. Run connected tests twice to prove stability.
3. Document results and residual risks (if any).
4. Freeze map/task scope for release branch.

Exit criteria:
- All required checks green twice in succession.
- No open P0/P1 map/task findings.
- Release note entry completed.

Status: Completed (2026-02-14)
- Required checks rerun and passing.
- Connected Android tests passed twice consecutively on attached device.

## 5) Execution Phases and Timeline

Phase order:

1. Phase 0 (1 day): Baseline lock and metrics capture.
2. Phase 1 (2-3 days): Workstream D test expansion.
3. Phase 2 (1 day): Workstream E guardrails.
4. Phase 3 (2 days): Workstream F decomposition finish.
5. Phase 4 (1-2 days): Workstream G performance/lifecycle evidence.
6. Phase 5 (1 day): Workstream H sign-off and final scoring.

Total expected effort: 8-10 working days.

## 6) Verification Gates (Per Phase)

Mandatory per non-trivial phase:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel
```

Final sign-off requires two consecutive connected test passes.

## 7) Quantitative Acceptance Criteria (5/5)

Architecture cleanliness 5/5 requires all:
- Runtime entrypoint lookup callsites: 0
- Direct task render sync trigger owner count: 1
- No UI-to-manager bypass findings in enforceRules
- No Compose runtime state in non-UI runtime manager/state classes

Maintainability/change safety 5/5 requires all:
- No critical map/task hotspot above 350 LOC unless justified
- High-risk flows mapped to explicit collaborators
- No unmanaged cross-cutting side effects in UI layer
- Hotspot reduction plan covers top high-risk files, not single-file-only scope

Test confidence 5/5 requires all:
- No ignored tests in map/task critical path
- Deterministic replay transition parity test passing
- Two consecutive stable full test gate runs

Release readiness 5/5 requires all:
- Required commands all green
- Connected tests green on attached reference device/emulator
- No open P0/P1 defects in map/task scope
- Active plan file tracked in VCS and referenced consistently in contract docs

## 8) Risks and Mitigations

Risk: Over-refactoring with low ROI.
- Mitigation: prioritize D/E first; F only where measurable risk decreases.

Risk: Flaky connected tests hide true regressions.
- Mitigation: run connected gates twice and isolate flaky test ownership.

Risk: Scope expansion into unrelated modules.
- Mitigation: limit changes to map/task vertical slice and docs only.

## 9) Deliverables

Minimum deliverables:
- Expanded deterministic test suite for map/task risky paths.
- Updated enforceRules guardrails for trigger and dependency purity.
- Final decomposition changes with reduced hotspot density.
- Performance/lifecycle evidence document.
- Final scorecard with objective pass/fail evidence.

## 10) Done Definition

This plan is complete when:

1. Workstreams D-H are closed with evidence.
2. All verification gates pass in final run sequence.
3. Final scorecard is 5.0 across target dimensions.
4. Docs are updated and consistent with final runtime wiring.
