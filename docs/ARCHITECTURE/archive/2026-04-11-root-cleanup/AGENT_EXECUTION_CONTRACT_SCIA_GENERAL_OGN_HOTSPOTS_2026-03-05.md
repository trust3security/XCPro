# AGENT_EXECUTION_CONTRACT_SCIA_GENERAL_OGN_HOTSPOTS_2026-03-05.md

Date: 2026-03-05
Owner: XCPro Team / Codex
Status: Active
Primary plan reference: `docs/ARCHITECTURE/CHANGE_PLAN_SCIA_TOGGLE_GENERAL_OGN_HOTSPOTS_UI_PARITY_2026-03-05.md`

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

This is the autonomous execution contract for moving SCIA global enable control to `General -> OGN` and aligning OGN sheet UX with Hotspots sheet conventions.

Authority:
- Execute all phases without checkpoint prompts unless blocked by missing repository context.
- Preserve MVVM + UDF + SSOT and dependency direction (`UI -> domain -> data`).
- Keep replay/live behavior unchanged and deterministic.
- Do not revert or modify unrelated workspace changes.

Completion definition:
- All phases complete in order.
- Per-phase basic build gate passes (or explicit unrelated-blocker evidence is recorded).
- Final required verification passes.
- Docs/tests reflect final wiring.

---

## 1) Mandatory Per-Phase Automation Loop

For each phase in Section 2, run this loop:

1. Implement only that phase scope.
2. Run phase basic build gate:
   - `./gradlew :app:assembleDebug`
3. If build fails:
   - fix phase-owned issues and rerun
   - if blocked by unrelated pre-existing failures, record blocker and run fallback:
     - `./gradlew :feature:map:assembleDebug`
4. Run phase-focused tests.
5. Record phase output:
   - files changed
   - build/test pass/fail
   - risks/blockers

Hard rule:
- Do not start the next phase until the current phase gate is green or blocked with explicit unrelated-blocker evidence.

Automation runner:
- `powershell -ExecutionPolicy Bypass -File scripts/qa/run_scia_ogn_hotspots_phase_gates.ps1`

---

## 2) Phased Execution Plan

### Phase 0 - Baseline Lock and Test Net

Scope:
- Lock baseline behavior for current SCIA toggle placement and OGN/Hotspots sheet host behavior.
- Ensure baseline tests are green before edits.

Phase focused checks:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.ui.MapBottomSheetTabsTest"`
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.ogn.OgnTrafficPreferencesRepositoryTest"`

Exit:
- Baseline tests pass.
- Basic build gate passes.

### Phase 1 - OGN Settings State/Intent Wiring

Scope:
- Add SCIA read/write path in `OgnSettingsUseCase`, `OgnSettingsViewModel`, and `OgnSettingsUiState`.
- No map tab UI removals yet.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsViewModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsUiState.kt`

Phase focused checks:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.screens.navdrawer.*Ogn*"`
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.ogn.OgnTrafficPreferencesRepositoryTest"`

Exit:
- OGN settings layer owns SCIA state/intents via existing repository SSOT path.
- Basic build gate passes.

### Phase 2 - OGN Sheet UX Parity + SCIA Top Placement

Scope:
- Add SCIA toggle at top of `General -> OGN`.
- Align OGN sub-sheet host pattern with Hotspots host pattern.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/OgnSettingsScreen.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntimeSheets.kt`

Phase focused checks:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.screens.navdrawer.*Settings*"`
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.ui.MapBottomSheetTabsTest"`

Exit:
- SCIA control is at top of General OGN settings.
- OGN sheet host behavior matches Hotspots host contract.
- Basic build gate passes.

### Phase 3 - Remove Duplicate SCIA Toggle from Map OGN Tab

Scope:
- Remove SCIA switch from map OGN tab and related callback plumbing.
- Keep map-tab behavior for OGN overlay + per-aircraft trail selection.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabContents.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabs.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntimeSections.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt` (if required)

Phase focused checks:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.ui.MapBottomSheetTabsTest"`
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.*Traffic*"`

Exit:
- No duplicate SCIA global toggle exists in map OGN tab.
- Basic build gate passes.

### Phase 4 - Hardening, Docs Sync, Finalization

Scope:
- Final docs/test sync and contract closeout evidence.
- Update `PIPELINE.md` only if wiring text changed materially.

Phase focused checks:
- `./gradlew :feature:map:testDebugUnitTest`

Exit:
- All planned behavior and tests are stable.
- Basic build gate passes.

---

## 3) Required Final Verification (Non-Negotiable)

After Phase 4:

- `python scripts/arch_gate.py`
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

When environment is available:

- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
- `./gradlew connectedDebugAndroidTest --no-parallel`

---

## 4) Architecture Drift Self-Audit (Must Pass)

- No new SSOT duplication for SCIA/OGN settings.
- No business logic moved into Composables.
- No direct forbidden time API usage introduced.
- No new hidden global mutable state.
- No manager/controller escape hatches added.
- No replay determinism change.

If any check fails:
- fix before completion, or
- record a time-boxed deviation in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` (issue, owner, expiry, removal plan).

---

## 5) Reporting Format Per Phase

At end of each phase report:
- Phase summary
- Files touched
- Basic build result (`:app:assembleDebug`)
- Focused tests result
- Risks/blockers

At final completion report:
- acceptance checklist
- verification command matrix (pass/fail)
- quality rescore (AGENT.md Section 6)
- rollback notes

