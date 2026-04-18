# AGENT_EXECUTION_CONTRACT_GENERAL_MAP_HOSTED_SHEET_2026-03-03.md

Date: 2026-03-03
Owner: XCPro Team / Codex
Status: Executed (2026-03-03)
Primary plan reference: `docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/CHANGE_PLAN_GENERAL_MAP_HOSTED_SHEET_2026-03-03.md`

Use with:
- `AGENTS.md`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`
- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

---

## 0) Agent Contract (Read First)

This is the autonomous execution contract for moving General settings from route-hosted ownership to MapScreen-hosted modal ownership, end-to-end, with production-grade verification.

Authority:
- Execute all phases without checkpoint prompts unless blocked.
- Preserve MVVM + UDF + SSOT and dependency direction (`UI -> domain -> data`).
- Do not revert or disturb unrelated workspace changes.

Completion definition:
- All phases complete in order.
- Mandatory basic build gate passes after each phase.
- Final required verification passes.
- Docs and tests reflect final wiring.

---

## 1) Mandatory Per-Phase Automation Loop

For every phase below, run this exact sequence:

1. Implement only that phase scope.
2. Run mandatory basic build gate:
   - `./gradlew :app:assembleDebug`
3. If basic build fails:
   - Fix phase-owned issues and rerun.
   - If blocked by unrelated pre-existing workspace failures, record blocker with file/task evidence and run fallback:
     - `./gradlew :feature:map:assembleDebug`
4. Run phase-specific focused test commands.
5. Record phase summary:
   - files changed
   - test/build pass/fail
   - residual risks

Hard rule:
- Do not start next phase until current phase basic build gate is green or an explicit unrelated-blocker record exists.

---

## 2) Phased Execution Plan

### Phase 0 - Baseline and Test Net

Scope:
- Recreate or replace deleted policy coverage for map General entry behavior.
- Add/refresh baseline tests for current shortcut/drawer/route behavior.

Phase focused checks:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.screens.navdrawer.GeneralSettingsScreenPolicyTest"`
- `./gradlew :app:testDebugUnitTest --tests "com.trust3.xcpro.MapOverlayWidgetGesturesTest"`

Phase exit:
- Baseline tests pass.
- Basic build gate passes.

### Phase 1 - Extract Reusable General Host

Scope:
- Extract General sheet host/content into reusable composable(s) that can run in map-owned context and route-owned compatibility context.
- Keep behavior parity.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntime.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntimeSheets.kt`

Phase focused checks:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.screens.navdrawer.GeneralSettingsScreenPolicyTest"`

Phase exit:
- Route-hosted General still functional.
- Reusable host available.
- Basic build gate passes.

### Phase 2 - Map Modal Ownership

Scope:
- Extend map modal owner with General modal state and close/back arbitration.
- Wire General host into MapScreen runtime layer.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/map/MapModalManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt`

Phase focused checks:
- modal manager/back-handler tests added and passing.

Phase exit:
- General can open/close in map context deterministically.
- Basic build gate passes.

### Phase 3 - Rewire Map Entry Points

Scope:
- Replace map settings shortcut route navigation with map-modal open callback.
- Replace drawer General route navigation (from map context) with map-modal open callback.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt`
- `feature/map/src/main/java/com/trust3/xcpro/navdrawer/NavigationDrawer.kt`
- `feature/map/src/main/java/com/trust3/xcpro/navdrawer/DrawerMenuSections.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt`

Phase focused checks:
- `./gradlew :app:testDebugUnitTest --tests "com.trust3.xcpro.MapOverlayWidgetGesturesTest"`
- New/updated map entrypoint tests pass.

Phase exit:
- Map shortcut + drawer General no longer call `navigate(SettingsRoutes.GENERAL)` in map path.
- Basic build gate passes.

### Phase 4 - Compatibility and Legacy Caller Migration

Scope:
- Migrate remaining callers (`MainActivityScreen` and other non-map callers).
- Keep temporary compatibility route if needed.
- Retire route-owner path only when no production caller remains.

Primary files:
- `app/src/main/java/com/trust3/xcpro/MainActivityScreen.kt`
- `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
- `feature/map/src/main/java/com/trust3/xcpro/navigation/SettingsRoutes.kt` (if deprecating)

Phase focused checks:
- caller migration tests.
- nav compatibility tests.

Phase exit:
- No required production path depends on route-hosted General owner.
- Basic build gate passes.

### Phase 5 - Hardening and Docs Sync

Scope:
- Finalize docs and pipeline notes.
- Ensure instrumentation/manual matrix instructions are current.

Primary files:
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/CHANGE_PLAN_GENERAL_MAP_HOSTED_SHEET_2026-03-03.md`
- this contract

Phase focused checks:
- no stale references to old ownership model in docs.

Phase exit:
- docs reflect final architecture.
- basic build gate passes.

---

## 3) Required Final Verification (Non-Negotiable)

After Phase 5 complete:

- `python scripts/arch_gate.py`
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

When environment is available:

- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
- `./gradlew connectedDebugAndroidTest --no-parallel`

---

## 4) Architecture Drift Self-Audit (Must Pass)

- No new SSOT duplication.
- No UI/business-logic leakage.
- No new direct time API usage in forbidden layers.
- No hidden global mutable state.
- No unresolved back-stack/modal ownership conflicts.
- No map entrypoint bypasses that reintroduce route transition.

If any item fails:
- fix before completion, or
- create time-boxed deviation entry in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (issue, owner, expiry, removal plan).

---

## 5) Reporting Format Per Phase

At the end of each phase, emit:

- Phase summary
- Files touched
- Basic build result (`:app:assembleDebug`)
- Focused tests result
- Risks/blockers

At final completion, emit:

- acceptance checklist
- full verification matrix
- quality rescore (architecture, maintainability, test confidence, release readiness)
- rollback notes

