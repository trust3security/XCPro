# CHANGE_PLAN_SCIA_TOGGLE_GENERAL_OGN_HOTSPOTS_UI_PARITY_2026-03-05.md

## 0) Metadata

- Title: Move SCIA global enable toggle to `General -> OGN` and align OGN sheet UX with Hotspots sheet
- Owner: XCPro map/ui
- Date: 2026-03-05
- Issue/PR: TBD
- Status: Draft
- Execution contract: `docs/ARCHITECTURE/AGENT_EXECUTION_CONTRACT_SCIA_GENERAL_OGN_HOTSPOTS_2026-03-05.md`

## 1) Scope

- Problem statement:
  - The SCIA global enable toggle is currently in the map bottom-sheet OGN tab (`MapBottomSheetTabContents.kt`), while persistent OGN preferences live under `General -> OGN`.
  - `General -> OGN` sheet host behavior and layout are close to, but not fully aligned with, the Hotspots settings sheet host/content conventions.
  - This splits one preference domain across two UI surfaces and creates non-uniform settings UX.
- Why now:
  - User request is explicit: move the SCIA enable toggle into `General -> OGN` at the top and make OGN UI match Hotspots style, without ad-hoc one-off changes.
- In scope:
  - Relocate SCIA global enable control from map bottom-sheet OGN tab into `OgnSettingsContent` top section.
  - Keep SCIA state ownership in existing SSOT (`OgnTrafficPreferencesRepository`).
  - Align `OgnSettingsSubSheet` host behavior with `HotspotsSettingsSubSheet` behavior (same modal pattern).
  - Align OGN settings content structure and spacing conventions with Hotspots content style while preserving OGN-specific controls.
  - Update tests for the new ownership path and removed bottom-sheet toggle surface.
- Out of scope:
  - Changes to OGN repository policies (streaming, radius algorithm, trail rendering).
  - Changes to SCIA per-aircraft selection semantics.
  - Replay/fusion/audio/domain logic changes.
  - New navigation destinations.
- User-visible impact:
  - SCIA enable/disable is controlled at `General -> OGN` (top control).
  - OGN settings sheet presents with the same host/content behavior style as Hotspots sheet.
  - Map bottom-sheet OGN tab no longer owns the global SCIA enable toggle.

## 2) Baseline Findings

1) SCIA global toggle currently lives in map tab UI.
- File: `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabContents.kt`
- Function: `OgnTabContent(...)` currently renders `Switch` for `Show Scia`.

2) SCIA state is already SSOT-backed.
- File: `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
- Owner flow: `showSciaEnabledFlow`
- Mutation API: `setShowSciaEnabled(...)` and atomic `setOverlayAndSciaEnabled(...)`.

3) OGN settings sheet currently does not expose SCIA control.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsUiState.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsScreen.kt`

4) Hotspots sheet host style is the target pattern for parity.
- File: `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/SettingsDfRuntimeSheets.kt`
- `HotspotsSettingsSubSheet(...)` uses:
  - `rememberModalBottomSheetState(skipPartiallyExpanded = true)`
  - `dragHandle = null`
  - full-height column with content in weighted body.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| SCIA global enabled flag | `OgnTrafficPreferencesRepository` | `showSciaEnabledFlow` -> ViewModel `StateFlow` | local remember/mutable state in map tab or settings UI |
| OGN overlay enabled flag | `OgnTrafficPreferencesRepository` | `enabledFlow` | duplicated map-only source of truth |
| OGN settings UI composition state | OGN settings composables | local transient slider/text draft state only | persistence mirrors outside ViewModel/repository |

### 3.2 Dependency Direction

Required direction remains:

`UI -> ViewModel/use-case -> repository (SSOT)`

Planned touched files stay inside existing boundaries:
- UI: `map/ui/*`, `screens/navdrawer/*`
- ViewModel/use-case: `screens/navdrawer/OgnSettings*`
- Data: existing `OgnTrafficPreferencesRepository` APIs only (no new owner).

### 3.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| SCIA global toggle UI owner | Map OGN bottom tab (`OgnTabContent`) | `General -> OGN` settings content (`OgnSettingsContent`) | consolidate preference controls in General settings and remove split ownership | UI tests + ViewModel tests |
| OGN sub-sheet host style contract | OGN-specific modal shape | Hotspots-style modal host contract | consistent behavior and no ad-hoc sheet variant | compose behavior tests |

### 3.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `OgnTabContent` direct SCIA toggle mutation callback (`onShowSciaEnabledChanged`) | Map tab mutates persistent SCIA preference directly | Remove map-tab SCIA toggle; drive SCIA from `General -> OGN` ViewModel action | Phase 2 |
| OGN sheet state lacking SCIA field | UI reads only icon/radius/mode/IDs | Extend `OgnSettingsUiState` with SCIA + overlay values from use-case flows | Phase 1 |

### 3.3 Time Base

No time-dependent domain logic changes.

| Value | Time Base | Why |
|---|---|---|
| SCIA toggle visibility/state in settings | N/A (UI preference rendering) | not a timed computation path |

Explicitly forbidden:
- introducing wall-time logic for UI settings behavior
- any replay/live branching for this UI refactor

### 3.4 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence: None added; settings UI relocation only

### 3.5 Boundary Adapter Check

No new external I/O boundary introduced.
Existing adapter/SSOT path remains:
`OgnSettingsViewModel -> OgnSettingsUseCase -> OgnTrafficPreferencesRepository`.

## 4) Data Flow (Before -> After)

Before:

`Map OGN tab switch -> MapScreen callback -> MapScreenTrafficCoordinator.onToggleOgnScia() -> OgnTrafficUseCase -> OgnTrafficPreferencesRepository`

After:

`General -> OGN top switch -> OgnSettingsViewModel -> OgnSettingsUseCase -> OgnTrafficPreferencesRepository`

Unchanged supporting path:

`OGN target details sheet per-aircraft action -> MapScreenTrafficCoordinator.onToggleOgnScia()` remains as existing fallback/assist behavior.

## 5) Phased Implementation

### Phase 0 - Baseline and Contract Lock

- Goal:
  - Lock existing behavior and establish exact acceptance targets before edits.
- Files:
  - `docs/ARCHITECTURE/CHANGE_PLAN_SCIA_TOGGLE_GENERAL_OGN_HOTSPOTS_UI_PARITY_2026-03-05.md` (this file).
- Tasks:
  - Document current SCIA toggle owner and target owner.
  - Enumerate UI callsites that render SCIA toggle.
- Exit criteria:
  - Scope and architecture contract are explicit; no ad-hoc implementation allowed outside this plan.

### Phase 1 - OGN Settings State and Intent Wiring

- Goal:
  - Make `OgnSettings*` stack fully capable of owning SCIA global toggle.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsUiState.kt`
- Tasks:
  - Add SCIA flow exposure to use-case.
  - Add SCIA flag to OGN UI state.
  - Add `setShowSciaEnabled(...)` intent method in ViewModel.
  - Optionally expose OGN overlay-enabled flag in OGN UI state if needed for guard text/state coupling.
- Tests:
  - Add/update ViewModel tests validating SCIA flow mapping and mutation dispatch.
- Exit criteria:
  - OGN settings layer can read/write SCIA flag without touching map tab code.

### Phase 2 - OGN Sheet UI Parity with Hotspots + SCIA Toggle Relocation

- Goal:
  - Move SCIA toggle to `General -> OGN` top and align OGN sheet behavior/style with Hotspots conventions.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OgnSettingsScreen.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/SettingsDfRuntimeSheets.kt`
- Tasks:
  - Add top SCIA card/control in `OgnSettingsContent` (first section in scroll content).
  - Keep existing OGN controls (icon size/radius/display mode/IDs) below SCIA section.
  - Align OGN sub-sheet host with Hotspots host contract:
    - `rememberModalBottomSheetState(skipPartiallyExpanded = true)`
    - `dragHandle = null`
    - full-height column + weighted content body.
  - Keep all logic in ViewModel/use-case/repository; UI remains render + intent dispatch only.
- Tests:
  - Add/adjust Compose tests for OGN settings content section order and SCIA toggle presence.
- Exit criteria:
  - SCIA toggle is visible at top of `General -> OGN`.
  - OGN sheet host behavior matches Hotspots sheet behavior.

### Phase 3 - Map OGN Bottom-Sheet Simplification

- Goal:
  - Remove global SCIA toggle from map OGN tab and keep it focused on trail visibility context.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabContents.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSections.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt` (if signature propagation needed)
- Tasks:
  - Remove SCIA switch row and its callback plumbing from OGN tab surface.
  - Keep user guidance text pointing to `General -> OGN` for SCIA global enable.
  - Preserve OGN overlay switch and aircraft-row behavior.
  - Preserve details-sheet fallback that can auto-enable SCIA when selecting per-aircraft trail on.
- Tests:
  - Update `MapBottomSheetTabsTest` expectations (no `Show Scia` row in OGN tab).
  - Add regression assertion for guidance text/path.
- Exit criteria:
  - No SCIA global enable control remains in map OGN tab.
  - Existing map interactions continue to function.

### Phase 4 - Hardening, Docs, and Verification

- Goal:
  - Ensure no architecture drift, deterministic behavior preservation, and complete evidence.
- Files:
  - Update `docs/ARCHITECTURE/PIPELINE.md` only if settings wiring description requires correction.
  - Tests touched in previous phases.
- Tasks:
  - Run required quality gates.
  - Confirm no new deviations required; if needed, file in `KNOWN_DEVIATIONS.md` with issue/owner/expiry.
- Exit criteria:
  - Required verification passes.
  - Docs and tests reflect final ownership path.

## 6) Test Plan

- Unit/ViewModel:
  - OGN settings ViewModel maps SCIA flow to UI state.
  - OGN settings ViewModel dispatches `setShowSciaEnabled(...)`.
- Compose/UI tests:
  - OGN settings content shows SCIA toggle at top section.
  - OGN sub-sheet parity behavior matches Hotspots host contract.
  - Map OGN tab no longer shows SCIA toggle.
- Regression:
  - Existing `OgnTrafficPreferencesRepositoryTest` SCIA tests remain green.
  - Existing map-bottom-tabs behavior tests updated for intentional UI surface move.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant (device/emulator):

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Hidden coupling between SCIA and OGN overlay toggles | medium UX regressions | keep existing coordinator semantics; only move UI surface | XCPro map/ui |
| OGN sheet parity work causes layout regressions on small screens | medium | match Hotspots host scaffolding exactly; add compact-width compose checks | XCPro map/ui |
| Signature churn across map UI layers | low-medium | perform phased callback removal with compile-safe propagation and targeted tests | XCPro map/ui |
| User confusion after toggle relocation | medium | include explicit helper text in OGN map tab to point to `General -> OGN` | XCPro map/ui |

## 8) Acceptance Gates

- SCIA global toggle is owned by `General -> OGN` UI and wired through existing ViewModel/use-case/repository path.
- Map OGN tab does not expose a duplicate SCIA global toggle.
- OGN sub-sheet host behavior matches Hotspots host pattern.
- No violations of `ARCHITECTURE.md` / `CODING_RULES.md`.
- No new duplicate SSOT owners.
- Required checks pass, or approved exception is logged in `KNOWN_DEVIATIONS.md`.

## 9) Rollback Plan

- Revert unit:
  - Phase 3 map-tab simplification can be reverted independently if in-map usability regresses.
  - Phase 2 OGN sheet parity can be reverted while keeping Phase 1 state wiring.
- Recovery steps:
  1. Restore SCIA switch in `OgnTabContent`.
  2. Keep `OgnSettings*` SCIA wiring in place (safe additive path).
  3. Re-run verification gates and compare behavior.

## 10) AGENT.md Compliance Mapping

This section maps this plan to `docs/ARCHITECTURE/AGENT.md` so execution can be audited against the repository contract.

### 10.1 Mandatory Pre-Implementation Gate

| AGENT.md Gate | Plan Evidence |
|---|---|
| 1.1 SSOT Ownership | Section `3.1 SSOT Ownership` |
| 1.2 Dependency Direction | Section `3.2 Dependency Direction` |
| 1.3 Time Base Declaration | Section `3.3 Time Base` |
| 1.4 Replay Determinism | Section `3.4 Replay Determinism` |
| 1.5 Boundary Adapter Check | Section `3.5 Boundary Adapter Check` |

### 10.2 Phased Execution Contract Mapping

| AGENT.md Phase | This Plan Phase | Gate |
|---|---|---|
| Phase 0 - Baseline | Phase 0 | Contract locked; behavior baseline documented |
| Phase 1 - Pure Logic | Phase 1 | OGN settings state/intent wiring complete with tests |
| Phase 2 - Repository/SSOT Wiring | Phase 1 and Phase 2 | Single owner preserved; no duplicate state introduced |
| Phase 3 - ViewModel/UI Wiring | Phase 2 and Phase 3 | UI path migrated; map tab surface simplified; no bypass |
| Phase 4 - Hardening | Phase 4 | Required verification commands pass; docs/tests synced |

## 11) Required Verification and Evidence (AGENT 4 + 4A)

Minimum commands:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

Evidence reporting must include:
- pass/fail for each command
- first failing paths when any gate fails
- file list touched in each phase
- explicit note whether `PIPELINE.md` changed or not
- explicit note whether `KNOWN_DEVIATIONS.md` changed or not

## 12) Mandatory Architecture Drift Self-Audit (AGENT 5)

Before marking complete, verify:
- no UI imports in domain code
- no data-layer imports in UI composables beyond existing allowed use-case/viewmodel boundaries
- no direct system time usage introduced in domain/fusion paths
- no new global mutable state
- no manager/controller escape-hatch reintroduction
- no duplicate SSOT owners for SCIA/OGN settings

If any item fails, fix before completion or record approved deviation in `KNOWN_DEVIATIONS.md`.

## 13) Mandatory Quality Rescore Template (AGENT 6)

Post-implementation, attach evidence-based scores:

- Architecture cleanliness: __ / 5
- Maintainability and change safety: __ / 5
- Test confidence on risky paths: __ / 5
- Overall map/task slice quality: __ / 5
- Release readiness (map/task slice): __ / 5

Each score must include:
- file/test evidence
- remaining risks
- explanation if score is below 4.0
