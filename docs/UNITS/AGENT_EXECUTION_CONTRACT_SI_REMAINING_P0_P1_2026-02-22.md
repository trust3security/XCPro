# Agent-Execution-Contract.md -- SI Remaining Work (Items 1-5)

Date: 2026-02-22
Owner: XCPro Team / Codex
Status: In progress (Run 43 `#18` five-pass re-check complete; compatibility-wrapper cut remains open)
Backlog reference: `docs/UNITS/EXECUTION_BACKLOG_SI_MIGRATION_2026-02-22.md`

Use with:
- `AGENTS.md`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/UNITS/Agent-Execution-Contract-SKELETON.md` (source template)

Run 38 update:
- Closed residual backlog items `#28/#34/#40/#41/#42/#43` with implementation and verification evidence
  logged in UNITS backlog, change-plan, findings, and verification-matrix docs.

Run 43 update:
- Executed five-pass static re-check focused on `#18`.
- Open residual scope is now narrowed to:
  - active AAT km wrapper route (VM/use-case/coordinator/delegate/manager/configurator),
  - gesture/camera `radiusKm` internal contract path,
  - dead km compatibility wrappers pending removal plus km-coupled tests,
  - missing `enforce_rules` coverage for `#18` closure protection.

---

# 0) Agent Execution Contract (Read First)

This document is the task-level execution contract for finishing SI migration work items 1-5 from the current deep re-pass.

## 0.1 Authority
- Execute end-to-end without checkpoint confirmations.
- Ask questions only when blocked by missing information that cannot be inferred from repository context.
- If ambiguity exists, choose the most architecture-consistent option and record assumptions.

## 0.2 Responsibilities
- Implement all scoped work in Section 1.
- Preserve MVVM + UDF + SSOT and dependency direction (`UI -> domain -> data`).
- Keep domain logic testable, deterministic, and Android-free.
- Keep replay deterministic for identical inputs.
- Fix build/test/lint failures introduced by this work.

## 0.3 Workflow Rules
- Execute phases in order.
- No partial implementations or deferred TODOs in production paths.
- Keep unit boundaries explicit: internal SI, boundary conversion only at I/O/UI/protocol seams.
- Update documentation whenever wiring/contracts materially change.

## 0.4 Definition of Done
Work is complete only when:
- All phases are complete with gates satisfied.
- Acceptance criteria in Section 3 are met.
- Required verification in Section 4 passes.
- Verification evidence table in Section 4.1 is filled.
- Local limitations (if any) are recorded in Section 4.2.
- Architecture drift self-audit (Section 6) is complete.
- Quality rescore (Section 7) is complete with evidence.

## 0.5 Mandatory Read Order
- `AGENTS.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
- `docs/ARCHITECTURE/AGENT.md`

---

# 1) Change Request (Filled)

## 1.1 Feature Summary
Complete the remaining SI migration so internal task/OGN/replay logic is meter-first end-to-end. Remove residual km-native contracts in AAT math/editing and ad-hoc conversion hotspots. Add tests and guardrails so regression back to mixed units is blocked.

## 1.2 User Stories / Use Cases
- As a pilot, I want task geometry, zone detection, and replay behavior to be unit-consistent so competition logic is reliable.
- As a developer, I want explicit SI contracts with boundary-only conversions so refactors are safe and bugs are traceable.

## 1.3 Scope and Non-Goals
- In scope:
  - Item 1: P0 migrate AAT core math APIs to meter-first.
  - Item 2: P1 migrate remaining AAT interaction/editing internals to meter contracts.
  - Item 3: P1 remove remaining ad-hoc km->m conversions in racing replay/coordinator paths.
  - Item 4: P1 migrate OGN subscription movement policy API to meter-first internal contract.
  - Item 5: Verification + cleanup track (tests, wrappers cleanup targets, static checks, final SI doc sync notes).
- Out of scope:
  - Rewriting unrelated features or redesigning UI flows.
  - Introducing non-SI internal contracts in any new code.

## 1.4 Constraints
- Modules/layers affected:
  - `feature/map` tasks AAT/racing/replay/OGN internals.
  - `docs/UNITS` compliance docs.
- Performance/battery limits:
  - No added high-frequency allocations in map/replay loops.
  - No additional background polling.
- Compatibility/migrations:
  - Preserve boundary compatibility for existing persisted/protocol km fields where required.
  - Keep public behavior parity unless bug-fix intent is explicit.
- Safety/compliance constraints:
  - Internal math contracts remain SI-only.
  - No architecture boundary violations.

## 1.5 Inputs / Outputs
- Inputs:
  - GPS/task waypoints/assigned areas/replay route points/OGN positions.
- Outputs:
  - Task geometry checks, zone intersection checks, replay anchors, OGN movement-threshold decisions, and unit-tested APIs.

## 1.6 Behavior Parity
- Preserve functional behavior for start/finish/turnpoint detection semantics.
- Preserve replay determinism for same input streams.
- Preserve external km display/protocol behavior at explicit boundaries only.

## 1.7 Time Base Declaration
| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Replay anchor calculations | Replay | Must remain deterministic and driven by replay samples |
| OGN movement threshold checks | Monotonic-independent spatial math | Spatial-only; no wall-time dependency introduced |

## 1.8 SSOT Ownership Declaration
- Data item: internal task/replay/OGN distances and radii.
  - Authoritative owner: domain/task/replay logic in `feature/map`.
  - Exposed as: SI (`*Meters`) internally.
  - Forbidden duplicates: parallel km-native internal state or mixed-unit comparisons.

## 1.9 Work Item Mapping (1-5)
1. `P0`: `AATMathUtils` and `AATGeometryGenerator` meter-first APIs and caller migration.
2. `P1`: AAT edit/map internals (`AATEditModeState`, `AATAreaTapDetector`, `AATMovablePointStrategySupport`, `AATEditGeometry`).
3. `P1`: racing replay/coordinator conversion hotspots (`legacy map replay anchor helper`, `TaskManagerCoordinator`).
4. `P1`: OGN movement policy meter-first API (`OgnSubscriptionPolicy`).
5. `P1/P2`: verification expansion + cleanup items from SI backlog.

---

# 2) Execution Plan (Agent Owns)

## Phase 0 -- Baseline and Safety Net
- Inventory all remaining km-native contracts and ad-hoc conversions in scoped files.
- Lock current behavior with targeted regression tests before contract edits where risk is high.
- Confirm current build/test baseline for scoped modules.

Gate: repo builds; baseline tests green.

## Phase 1 -- Pure Logic Implementation (P0)
- Add meter-first math APIs in AAT core utilities:
  - `calculateDistanceMeters` and meter destination helpers.
- Migrate `AATGeometryGenerator` internal calculations and call sites to meters.
- Keep km wrappers only as explicit compatibility shims, marked for later cleanup.

Gate: deterministic unit tests pass; no mixed-unit comparisons in migrated code.

## Phase 2 -- Internal Wiring (P1)
- Migrate AAT map/editing internals to meter contracts.
- Replace remaining ad-hoc km->m conversions in replay/coordinator with centralized meter helpers.
- Migrate OGN movement threshold internals to meter-first API, preserving km string/filter boundary output where needed.

Gate: dependency direction preserved; no duplicate unit contracts.

## Phase 3 -- ViewModel/UI Boundary Check
- Confirm VM/UI consume explicit unit-safe outputs (no business math leakage).
- Confirm boundary conversion points are explicit and isolated.
- Remove or deprecate transitional wrappers no longer needed by active callers (as safe within scope).

Gate: end-to-end behavior works in debug with parity maintained.

## Phase 4 -- Hardening and Cleanup
- Expand tests for boundary adapters and fixture coverage gaps.
- Add/verify static enforcement targets for SI drift where applicable.
- Update SI docs and completion notes.

Gate: Section 4 commands pass and documentation is synced.

## 2.5 Documentation Sync Rules
- Update `docs/UNITS/EXECUTION_BACKLOG_SI_MIGRATION_2026-02-22.md` status as items close.
- If architecture/policy wording changes are needed, update:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
- If any temporary deviation is required, add it to `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` with issue/owner/expiry.

---

# 3) Acceptance Criteria

## 3.1 Functional Criteria
- [x] Given any AAT distance/radius internal calculation, when executed in domain/edit/replay paths, then SI meters are used internally without km-vs-meter comparisons.
- [x] Given replay anchor and coordinator turnpoint logic, when radius/line-width values are consumed, then conversions are centralized and explicit (no ad-hoc scatter).
- [x] Given OGN movement threshold policy, when evaluating movement, then internal decision math is meter-first and protocol/output boundaries remain explicit.

## 3.2 Edge Cases
- [x] Zero/negative/null radius handling remains safe and deterministic.
- [x] Start/finish/turnpoint role-specific behavior remains correct after migration.
- [x] Replay route edge cases (short legs, line gates, cylinder gates) remain deterministic and stable.
- [x] No regression in AAT edit hit detection or movement tolerance semantics.

## 3.3 Required Test Coverage
- [x] Unit tests for new meter-first AAT math APIs and wrapper parity.
- [x] Unit tests for AAT edit/map internals using meter contracts.
- [x] Unit tests for replay/coordinator conversion hotspots.
- [x] Unit tests for OGN policy meter-first behavior.
- [x] Existing SI regression suite remains green.

---

# 4) Required Verification

Minimum:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Run when relevant (device/emulator available):
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`

Release/CI verification:
- `./gradlew connectedDebugAndroidTest --no-parallel`

Optional quality checks:
- `./gradlew detekt`
- `./gradlew ktlintCheck`

## 4.1 Verification Evidence Table
| Command | Purpose | Result (PASS/FAIL) | Duration | Failures fixed | Notes |
|---|---|---|---|---|---|
| `./gradlew --no-daemon --no-configuration-cache :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.*" --tests "com.trust3.xcpro.tasks.aat.*" --tests "com.trust3.xcpro.tasks.racing.*"` | Focused SI regression in task/AAT/racing scope | PASS | ~1m31s | 1 compile fix | Caught stale import in `TaskPersistenceAdapters`; rerun green. |
| `./gradlew --no-daemon --no-configuration-cache enforceRules` | Architecture/coding rule enforcement | PASS | ~13s | N/A | SI static guards still pass after Run 10 wrapper/radius cleanup. |
| `./gradlew --no-daemon --no-configuration-cache testDebugUnitTest` | Unit/regression test coverage | PASS | ~51s | N/A | Full JVM suite green. |
| `./gradlew --no-daemon --no-configuration-cache assembleDebug` | Build integrity | PASS | ~44s | N/A | Debug app + modules assembled successfully. |
| `./gradlew --no-daemon --no-configuration-cache :app:uninstallDebug :app:uninstallDebugAndroidTest` | Instrumentation remediation | PASS | ~11s | N/A | Cleared stale app/test APKs on device before retry. |
| `./gradlew --no-daemon --no-configuration-cache :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` | App-module instrumentation (when relevant) | PASS | ~31s | 1 environment fix | Rerun passed (9 tests) after uninstall/reinstall remediation. |
| `./gradlew --no-daemon --no-configuration-cache connectedDebugAndroidTest --no-parallel` | Full multi-module instrumentation (release/CI parity) | FAIL | ~2m09s | N/A | Run was user-aborted to reduce cycle time before completion. |
| `./gradlew --no-daemon --no-configuration-cache :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.core.TaskWaypointRadiusContractTest" --tests "com.trust3.xcpro.tasks.domain.engine.DefaultAATTaskEngineTest" --tests "com.trust3.xcpro.tasks.aat.gestures.AatGestureHandlerHitTest" --tests "com.trust3.xcpro.tasks.TaskPersistSerializerFidelityTest" --tests "com.trust3.xcpro.tasks.TaskManagerCoordinatorTest"` | Focused validation of Run 19 `#30` code changes | PASS | ~54s | N/A | Validated touched task/AAT/racing paths after dual-write cleanup and meter-only call-path updates. |
| `./gradlew --no-daemon --no-configuration-cache enforceRules` | Architecture/coding rule enforcement (Run 19 rerun) | PASS | ~13s | N/A | No new SI rule regressions after Run 19 patch set. |
| `./gradlew --no-daemon --no-configuration-cache testDebugUnitTest` | Full JVM regression suite (Run 19 rerun) | PASS | ~45s | N/A | Full unit suite remains green after Run 19 changes. |
| `./gradlew --no-daemon --no-configuration-cache assembleDebug` | Build integrity (Run 19 rerun) | PASS | ~41s | N/A | Debug assemble remains green after Run 19 changes. |
| `./gradlew --no-daemon --no-configuration-cache :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.core.TaskWaypointRadiusContractTest" --tests "com.trust3.xcpro.tasks.core.TaskWaypointCustomParamsTest" --tests "com.trust3.xcpro.tasks.TaskPersistSerializerFidelityTest"` | Focused validation of Run 21 SI-first radius contract test updates | PASS | ~16s | N/A | Confirms SI-canonical internal expectations + explicit legacy boundary fallback coverage. |
| `./gradlew --no-daemon --no-configuration-cache :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.tasks.domain.engine.DefaultAATTaskEngineTest" --tests "com.trust3.xcpro.tasks.TaskManagerCoordinatorTest"` | Regression sanity for core task engine/coordinator contracts after Run 21 serializer hardening | PASS | ~47s | N/A | No regressions from clearing legacy `customRadius` on `toTask` import. |
| `./gradlew --no-daemon --no-configuration-cache :feature:map:compileDebugKotlin` | Compile integrity for Run 21 task/persistence changes | PASS | ~11s | N/A | Module compile clean post-hardening. |
| `./gradlew --no-daemon --no-configuration-cache enforceRules` | Architecture/coding rule enforcement (Run 21 rerun) | PASS | ~10s | N/A | SI guardrails remain green after Run 21 updates. |

## 4.2 Local Limitations Rule
If any required verification command cannot be run locally, record:
- Exact command not run.
- Exact blocker.
- Partial evidence run instead.
- Required follow-up verification step and owner.
- Residual shipping risk.

Recorded limitations for this execution:
- `./gradlew --no-daemon --no-configuration-cache connectedDebugAndroidTest --no-parallel`
  - Blocker: run intentionally interrupted by user to reduce execution time.
  - Partial evidence: app instrumentation (`:app:connectedDebugAndroidTest`) passed after remediation; all JVM/build gates passed.
  - Follow-up: rerun full multi-module instrumentation when full-cycle verification is required.
  - Residual risk: release/CI parity instrumentation evidence is not refreshed in this run.

---

# 5) Notes / ADR

- Decision: keep compatibility wrappers only at explicit boundaries while converging internals to SI.
- Alternative considered: hard cut removal of km APIs in one pass.
- Why chosen: lower risk for ongoing branch while preserving deterministic behavior.
- Risks:
  - Hidden km assumptions in secondary helpers.
  - Regressions in AAT edit interaction tolerances.
- Mitigation:
  - Focused unit tests and explicit grep audit for unit-contract patterns.

## 5.1 Architecture Exception Template
Use only if required. Otherwise keep `KNOWN_DEVIATIONS.md` unchanged.

---

# 6) Architecture Drift Self-Audit

Verify before completion:
- [x] No business logic moved into UI.
- [x] No UI/data dependency direction violations.
- [x] No direct system time calls in domain/fusion logic.
- [x] No new hidden global mutable state.
- [x] No new manager/controller escape hatches through VM/use-case APIs.
- [x] Replay remains deterministic for identical inputs.
- [x] No new rule violations, or a documented temporary deviation exists.

Quality rescore (post execution):
- Architecture cleanliness: 4.7 / 5
  - Evidence: Run 10 removed remaining AAT km wrapper surfaces (`AATTaskCalculator`/`AATSpeedCalculator`/`AATGeoMath`) and moved `AATRadiusAuthority` to meter-canonical APIs.
- Maintainability/change safety: 4.6 / 5
  - Evidence: wrapper cleanup reduced internal ambiguous APIs; persistence adapter now consumes authority meters directly.
- Test confidence on risky paths: 4.4 / 5
  - Evidence: focused task/AAT/racing suite plus full JVM gates green; app instrumentation rerun passed after remediation.
- Overall map/task slice quality: 4.5 / 5
  - Evidence: P0/P1 SI contract migration largely complete; remaining debt is primarily radius dual-contract cleanup and boundary hardening.
- Release readiness (map/task slice): 4.3 / 5
  - Evidence: required JVM/build checks and app instrumentation pass, but full multi-module instrumentation was user-aborted to save time; remaining risk includes deferred polar/radius compatibility cleanup.

---

# 7) Agent Output Format

At end of each phase:

## Phase N Summary
- What changed:
- Files touched:
- Tests added/updated:
- Verification results:
- Risks/notes:

At final completion:
- Done checklist (Sections 0.4 and 6)
- Quality rescore:
  - Architecture cleanliness: __ / 5
  - Maintainability/change safety: __ / 5
  - Test confidence on risky paths: __ / 5
  - Release readiness: __ / 5
- PR-ready summary (what/why/how)
- Manual verification steps (2-5 steps)

---

# 8) Next Planned Sequence (Post-Completion)

1. Execute compliance closeout:
   - `#28`/`#34` legacy/dead km helper cleanup.
   - `#40` active AAT area-size contract cleanup (`km2` helper-chain + policy path).
   - `#41` static guard expansion for `#28/#34/#40/#43` closure protection.
   - `#42` targeted quick-validation test hardening for `#40` area-size contract and labeling.
   - include adjacent dead/unused km-helper surfaces identified in Run 25 (`AATLongPressOverlay`, `AATEditGeometry` km wrapper).
   - `#43` `AATGeometryGenerator` unused km compatibility-wrapper cleanup.
2. Final verification parity:
   - rerun full instrumentation matrix when release/CI parity evidence is required.

---

# 9) Autonomous Continuation Runbook (No Additional Prompting)

This section is the authoritative autonomous procedure for the remaining deferred sequence in Section 8.

## 9.1 Mandatory Run Order
Run A (`#18`), Run B (`#17`), and Run C (`#30`) are complete.
1. Run D: Backlog `#28/#34/#40/#41/#42/#43` cleanup items plus final compliance closeout.

## 9.2 Locked Decisions (No Re-Open During Execution)
1. Internal canonical units remain SI only.
2. Polar storage contract target is SI-normalized (`m/s`) as canonical persisted/internal value.
3. Legacy persisted polar `km/h` fields are compatibility-read only; no new km/h-only internal contracts are allowed.
4. Compatibility wrappers are allowed only at explicit boundary seams (protocol, persistence, display, external import/export).

## 9.3 Run A Procedure: Compatibility-Wrapper Cut
1. Inventory wrappers and legacy unit APIs in task/AAT/racing scopes.
2. Classify each wrapper as:
   - internal (remove/migrate), or
   - boundary (retain with explicit naming and comments).
3. Migrate internal callers to meter-first APIs and delete obsolete `*Km`/ambiguous deprecated methods.
4. Update or add tests covering migrated call paths and wrapper removals.
5. Update docs/backlog status for `#18`.
6. Explicitly include radius-contract cleanup scope:
   - migrate `AATRadiusAuthority` to meter-canonical internal APIs,
   - remove internal `TaskWaypoint.customRadius`/`resolvedCustomRadiusKm` dependencies,
   - normalize racing param contracts (`keyholeInnerRadius` / `faiQuadrantOuterRadius`) to meter-named internals.

Run A acceptance gate:
1. No internal task/AAT/racing manager/coordinator/use-case/viewmodel logic depends on `*Km` wrappers.
2. Remaining wrappers are boundary-only and explicitly labeled.
3. Section 4 verification commands pass.

## 9.4 Run B Procedure: Polar SI Storage Migration
1. Inventory polar/model/config contracts and persistence adapters (`core/common` + `feature/map` glider stack).
2. Introduce canonical SI storage fields/contracts (`*Ms`) for polar speeds and sink points.
3. Add compatibility read path for legacy persisted `km/h` values with deterministic conversion.
4. Ensure writes persist canonical SI values; keep km/h only at UI/input formatting/parsing boundaries.
5. Add migration/regression tests:
   - legacy km/h data reads to correct SI values,
   - round-trip persistence stability,
   - behavior parity in polar interpolation/STF call paths.
6. Update docs/backlog status for `#17`.

Run B acceptance gate:
1. No internal polar math/storage path relies on km/h values.
2. Compatibility read for legacy km/h data is covered by tests.
3. Section 4 verification commands pass.

## 9.5 Run C Procedure: Core/Racing Radius Dual-Contract Cleanup (`#30`)
1. Remove internal `resolvedCustomRadiusKm()` dependencies from active racing call paths:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/data/persistence/TaskPersistenceAdapters.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/domain/engine/DefaultRacingTaskEngine.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskCoreMappers.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskInitializer.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointListItems.kt`
2. Normalize racing custom parameter contracts to meter-named internals:
   - migrate `RacingWaypointCustomParams` fields/keys in `feature/map/src/main/java/com/trust3/xcpro/tasks/core/TaskWaypointCustomParams.kt`.
   - migrate resolver usage in `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskObservationZoneResolver.kt` to consume meter-canonical params.
3. Migrate racing model canonical radius storage to meters:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingTask.kt` (`racingWaypoints` bridge).
   - ensure internal manager/model call paths use meter-only factory inputs; retain km compatibility inputs only for explicit boundary-adapter reads if still required.
4. Migrate unsuffixed radius update APIs in non-boundary task layers to explicit meter contracts:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskManagerCoordinator.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetCoordinatorUseCase.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetViewModel.kt`
   - naming target: `gateWidthMeters`, `keyholeInnerRadiusMeters`, `faiQuadrantOuterRadiusMeters`.
5. Migrate `RacingWaypointManager` to meter-canonical defaults/mutations and update dependent callers/signature paths:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointManager.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationEngineSupport.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/navigation/RacingZoneDetector.kt`
   - include residual raw field access cleanup in racing diagnostics/display paths (for example `RacingFinishLineDisplay`).
6. Migrate `RacingTaskManager` bridge/update radius contracts to explicit meter-named APIs and propagate through call chain:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingTaskManager.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskManagerCoordinator.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetCoordinatorUseCase.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetViewModel.kt`
7. Remove internal racing import-path km conversion in task sheet import flow:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/TaskSheetViewModel.kt` (`applyRacingObservationZone`).
8. Tighten boundary ownership for `TaskWaypoint.customRadius` km:
   - internal task/racing flows should use meter contracts.
   - km compatibility remains only in explicit serializer/protocol persistence boundaries (`TaskPersistSerializer` + legacy persistence bridges).
   - include core helper cleanup so `TaskWaypoint.withCustomRadiusMeters(...)` no longer forces km mirror propagation through internal engine normalization paths.
9. Remove dead km compatibility helpers from core task model once callers are migrated:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/core/Models.kt` (`withCustomRadiusKm`, `getEffectiveRadius`).
10. Remove dead racing km helper and migrate test fixtures:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/models/RacingWaypoint.kt` (`effectiveRadius`).
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationEngineSupportTest.kt`
   - `feature/map/src/test/java/com/trust3/xcpro/tasks/racing/navigation/RacingNavigationEngineTest.kt`
11. Update/add tests to lock SI-first behavior and compatibility boundaries:
   - `TaskWaypointRadiusContractTest`
   - `TaskWaypointCustomParamsTest`
   - `TaskPersistSerializerFidelityTest`
   - targeted racing mapper/engine tests for meter-first paths.
12. Update docs/backlog state (`EXECUTION_BACKLOG`, `CHANGE_PLAN`, `SI_REPASS_FINDINGS`) with final `#30` closure or residuals.
13. Remove internal racing UI dependence on legacy km radius fields:
   - `feature/map/src/main/java/com/trust3/xcpro/tasks/racing/RacingWaypointListItems.kt` should not read `TaskWaypoint.customRadius` for internal state/keys after meter-only migration.

Run C acceptance gate:
1. No internal task/racing mapper/engine/manager/UI path depends on `resolvedCustomRadiusKm()` or km-only radius fields.
2. `RacingWaypointCustomParams` + OZ resolver are meter-canonical internally; compatibility reads are boundary-scoped and tested.
3. No unsuffixed km-semantic radius APIs remain in coordinator/use-case/viewmodel update paths.
4. `RacingWaypoint`/`RacingTask` and `RacingWaypointManager` radius storage/defaulting are meter-first.
5. Internal `RacingWaypoint` factory invocation paths are meter-only; km compatibility inputs are boundary-scoped only.
6. `RacingTaskManager`/coordinator/use-case/viewmodel update APIs for racing radius are explicit meter contracts.
7. No internal racing import/update path converts meters back to km before manager/domain updates.
8. Core `TaskWaypoint` radius helper behavior no longer propagates km mirrors in internal AAT/racing engine normalization flows.
9. `TaskWaypoint` and `RacingWaypoint` dead km helper APIs are removed or strictly boundary-scoped.
10. Internal racing UI state wiring no longer reads legacy km radius fields (`TaskWaypoint.customRadius`) in non-boundary paths.
11. Section 4 verification commands pass.

## 9.6 Run D Procedure: Compliance Closeout
1. Execute deep SI re-pass and close remaining cleanup items `#28/#34/#40/#41/#42/#43`.
2. Close `#28`:
   - remove unused km helper `haversineDistance(...)` in `feature/map/src/main/java/com/trust3/xcpro/gestures/AirspaceGestureMath.kt`, or convert to explicit meter helper if retained by newly added callers.
3. Close `#34` target 1:
   - remove or migrate `AreaBoundaryCalculator.calculateDistanceInArea(...)` to explicit meter contract in `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/areas/AreaBoundaryCalculator.kt`.
   - if retained, rename to `calculateDistanceInAreaMeters(...)` and use meter helper accumulation only.
4. Close `#34` target 2:
   - remove legacy commented source in `feature/map/src/main/java/com/trust3/xcpro/tasks/KeyholeVerification.kt` (or move to non-production docs/test fixtures if historical reference is needed).
5. Close adjacent Run 25 residuals required for clean SI sign-off:
   - remove or migrate local km helper in `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ui/AATLongPressOverlay.kt` (file currently appears unused).
   - remove unused km wrapper set in `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/interaction/AATEditGeometry.kt` if no active callers require compatibility:
     - `generateCircleCoordinates(...)`
     - `generateSectorCoordinates(...)`
     - `calculateDestinationPoint(...)`
     - `haversineDistance(...)`
6. Close adjacent Run 34 residual (`#43`) for AAT geometry compatibility wrappers:
   - remove or boundary-scope unused km wrappers in `feature/map/src/main/java/com/trust3/xcpro/tasks/aat/geometry/AATGeometryGenerator.kt`:
     - `generateCircleCoordinates(...)`
     - `generateStartLine(...)`
     - `generateFinishLine(...)`
     - `calculateDestinationPoint(...)`
   - if retained for compatibility, enforce explicit boundary-only call sites and keep internal call chains on `*Meters` APIs.
7. Close `#40` area-size contract drift in active AAT quick-validation path:
   - normalize km2 helper chain (`CircleAreaCalculator` -> `SectorAreaGeometrySupport` -> `SectorAreaCalculator` -> `AreaBoundaryCalculator`) and `AATTaskQuickValidationEngine` policy checks to explicit SI m2 contracts, or boundary-scope km2 with explicit conversion wrappers.
   - if km2 is retained for warnings/output, ensure policy math remains SI and labels use squared units explicitly.
8. Close `#41` static guard gap:
   - extend `scripts/ci/enforce_rules.ps1` to prevent reintroduction of closed `#28/#34/#40/#43` patterns (dead km helpers/files, km wrappers, area-size unit-label drift, `AATGeometryGenerator` km-wrapper surfaces).
9. Close `#42` test hardening gap:
   - extend `AATTaskQuickValidationEngineUnitsTest` with area-size policy and squared-unit labeling assertions for `#40` path.
10. Re-run full verification matrix in Section 4.
11. Update:
   - `docs/UNITS/EXECUTION_BACKLOG_SI_MIGRATION_2026-02-22.md`
   - `docs/UNITS/CHANGE_PLAN_SI_UNITS_COMPLIANCE_2026-02-22.md`
   - `docs/UNITS/SI_REPASS_FINDINGS_2026-02-22.md`
   - `docs/UNITS/VERIFICATION_MATRIX_SI_2026-02-22.md`
   - this contract status and evidence table.

## 9.7 Autonomous Fail/Stop Rules
Continue autonomously unless one of these blockers occurs:
1. A required migration decision cannot be inferred from in-repo contracts/tests and would change persisted data semantics beyond Section 9.2.
2. Required files/modules are missing or irreconcilably inconsistent with the declared architecture rules.
3. Verification failures indicate unrelated pre-existing breakage that prevents attribution of regressions.

If blocked:
1. Apply only safe, non-breaking partial work.
2. Document exact blocker, affected files, and proposed decision.
3. Mark backlog item as blocked (not done) with owner/action.

## 9.8 Mandatory Command Set Per Run
Use these commands (Windows/PowerShell) for each run:
1. `./gradlew --no-daemon --no-configuration-cache enforceRules`
2. `./gradlew --no-daemon --no-configuration-cache testDebugUnitTest`
3. `./gradlew --no-daemon --no-configuration-cache assembleDebug`
4. `./gradlew --no-daemon --no-configuration-cache :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"` (when device/emulator available)
5. `./gradlew --no-daemon --no-configuration-cache connectedDebugAndroidTest --no-parallel` (release/CI parity run)

Inventory/re-pass helper commands (recommended):
1. `rg -n "Km\\b|km/h|Kmh|haversineKm|calculateDistance\\(" feature/map/src/main/java/com/trust3/xcpro/tasks feature/map/src/main/java/com/trust3/xcpro/ogn`
2. `rg -n "km/h|Kmh|speed.*Kmh|sink.*Kmh|Ms\\b" core/common/src/main/java/com/trust3/xcpro/common/glider feature/map/src/main/java/com/trust3/xcpro/glider`

## 9.9 Mandatory Reporting After Each Run
1. Phase summary (what changed, files touched, tests updated, risks).
2. Section 4.1 evidence table updates with pass/fail and duration.
3. Backlog item status transitions (`#30`, `#13` completion reference, `#28`, `#34` as applicable).
4. Explicit statement of compliance or residual risks/deferred items.
