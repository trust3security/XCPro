# THERMALLING_MODE_CHANGE_PLAN

## 0) Metadata

- Title: Thermalling Auto Mode + Zoom Automation
- Owner: XCPro Team
- Date: 2026-03-02
- Revision: 4 (Phase 2 live-only production-grade rescore sync)
- Issue/PR: TBD
- Status: In progress

## 0A) Current Phase Status

| Phase | Status | Notes |
|---|---|---|
| Phase 0 - Baseline | Completed | Feature branch has settings scaffolding merged locally. |
| Phase 1 - Settings SSOT | Completed | Score-uplift implementation landed (strings/localization, DataStore recovery, VM+Compose tests). |
| Phase 2 - Coordinator Logic | In progress | Coordinator/policy core implemented and verified; runtime ViewModel wiring still pending (Phase 3). |
| Phase 3 - ViewModel Wiring | Pending | No auto switch/zoom wiring in map runtime yet. |
| Phase 4 - Release Hardening | Pending | Full verification and rollout gating not complete. |

## 1) Scope

- Problem statement:
  - Pilots currently switch Thermal mode and zoom manually while circling.
  - This adds workload when entering/exiting thermals, especially in turbulence/wind drift.
- Why now:
  - The app already exposes a reliable circling signal (`isCircling`) and mode infrastructure.
  - Feature can build on existing SSOT/state flows with low architecture risk.
- In scope:
  - Thermalling automation settings.
  - Timer-based enter/exit state machine.
  - Optional mode switch and zoom behavior.
  - Restore previous mode/zoom on exit.
  - Dedicated settings screen entry.
- Out of scope:
  - Changing core circling detection thresholds in fusion domain.
  - Replay behavior changes in this pass (current implementation scope is live-only).
  - New flight mode taxonomy.
- User-visible impact:
  - Optional automatic Thermal mode + zoom with configurable delays.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Thermalling automation preferences | `ThermallingModePreferencesRepository` | `Flow<ThermallingModeSettings>` | UI-local or VM-local settings mirrors |
| Runtime thermalling automation state (`IDLE/ENTER_PENDING/ACTIVE/EXIT_PENDING`, pre-thermal snapshot) | `ThermallingModeCoordinator` | immutable state/events to VM | ad-hoc timer state in Composables |
| Current active map mode | `MapStateStore.currentMode` | `StateFlow<FlightMode>` | separate mode flags in UI |
| Current card flight mode | `MapStateStore.currentFlightMode` + `FlightDataManager.currentFlightModeFlow` | `StateFlow<FlightModeSelection>` | disconnected manual mirrors |
| Current zoom | `MapStateStore.currentZoom` | `StateFlow<Float>` | independent zoom caches outside runtime controller/coordinator |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched (implemented and planned):
  - `feature/map` settings repository/use-case/viewmodel/screen
  - `feature/map` runtime coordinator (implemented, not yet VM-wired)
  - `MapScreenViewModel` wiring (planned)
  - `app/AppNavGraph.kt` route wiring
- Boundary risk:
  - Timer/state-machine logic drifting into Composables.
  - VM bypassing use-case/repository boundaries for persistence.

### 2.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Thermalling auto mode policy | none | `ThermallingModeCoordinator` | central deterministic behavior | coordinator unit tests |
| Thermalling settings persistence | none | `ThermallingModePreferencesRepository` | SSOT + lifecycle-safe persistence | repository tests |

### 2.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | N/A | N/A (new feature) | N/A |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Enter/exit timer measurement | Monotonic (coordinator) | immune to wall-clock jumps |
| Flight sample signal (`isCircling`) | Existing fusion output stream timestamps | sourced from SSOT flight data stream |
| UI labels | Wall | display only |

Forbidden:

- Monotonic vs wall comparisons in logic.
- Replay-time vs wall-time comparisons in logic.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Settings I/O: DataStore/repository.
  - Coordination logic: lightweight state transitions in injected coroutine scope.
- Primary cadence/gating sensor:
  - `isCircling` from `CompleteFlightData`.
- Hot-path latency budget:
  - State transition handling < 10 ms/event on normal runtime profile.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (v1 disabled in replay).
- Randomness used: No.
- Replay/live divergence rules:
  - Live: coordinator logic is implemented and tested.
  - Replay: thermalling automation wiring is deferred; replay no-op guard will be added with Phase 3/4 runtime integration.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Business logic drifting into UI | ARCHITECTURE + CODING_RULES UI sections | tests + review | coordinator unit tests, UI render-only review |
| Duplicate settings ownership | SSOT rules | tests + review | repository/use-case/viewmodel tests |
| Hidden wall time in logic | Timebase rules | enforceRules + tests | injected clock in coordinator tests |
| Replay behavior regression | Determinism rules | deferred to Phase 3/4 integration tests | map/replay integration test paths (planned) |

## 3) Data Flow (Before -> After)

Before:

`FlightDataRepository -> MapScreenViewModel -> manual setFlightMode + manual map zoom`

After (target):

`FlightDataRepository(isCircling) + Thermalling settings repository -> ThermallingModeCoordinator -> MapScreenViewModel mode/zoom intents -> UI`

After (Phase 1 implemented):

`ThermallingSettingsScreen -> ThermallingSettingsViewModel -> ThermallingSettingsUseCase -> ThermallingModePreferencesRepository -> DataStore -> settingsFlow -> ViewModel -> UI`

## 4) Implementation Phases

### Phase 0 - Baseline (completed)

- Goal:
  - Lock current behavior before adding automation.
- Files:
  - Baseline tests (existing).
- Tests:
  - Existing manual mode switching and zoom behavior.
- Exit:
  - Baseline tests pass unchanged.

### Phase 1 - Settings SSOT (production-ready upgrade)

Goal:

- Keep the current functional settings implementation, but raise it to production readiness.

Current implementation inventory:

- `feature/map/src/main/java/com/trust3/xcpro/thermalling/ThermallingModeSettings.kt`
- `feature/map/src/main/java/com/trust3/xcpro/thermalling/ThermallingModePreferencesRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ThermallingSettingsUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ThermallingSettingsUiState.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ThermallingSettingsViewModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ThermallingSettingsScreen.kt`
- Route wiring:
  - `feature/map/src/main/java/com/trust3/xcpro/navigation/SettingsRoutes.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt`
  - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`

Production-upgrade work packages:

1. Preference contract hardening:
   - Freeze key names/defaults/ranges in docs and code comments.
   - Add explicit migration strategy for future key/schema changes.
2. Repository resilience:
   - Add read-failure fallback policy (`IOException` recovery to defaults).
   - Keep clamping on read and write, with targeted tests.
3. ViewModel/use-case contract hardening:
   - Verify UI state mapping parity for every setting.
   - Keep only use-case boundary calls from ViewModel.
4. Final UI string/localization cleanup:
   - Move all user-visible thermalling strings to string resources (no hard-coded UI copy).
   - Primary targets:
     - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ThermallingSettingsScreen.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt` (Thermalling tile label)
     - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt` / settings titles if thermalling labels are embedded there.
   - Use placeholder-based resources for dynamic labels:
     - enter delay label
     - exit delay label
     - zoom range/value labels
   - Use locale-safe number formatting for zoom and timer text (no default-locale ambiguity).
   - Ensure accessibility labels/state descriptions remain meaningful after string extraction.
5. Test coverage expansion:
   - Repository behavior tests (defaults, clamping, persistence, corruption fallback).
   - ViewModel mapping and intent tests.
   - Compose screen tests for control enable/disable and persisted state reflection.
6. Verification evidence:
   - Run full required gates and capture pass/fail status in PR notes.

Phase 1 exit criteria (production-ready):

- No hard-coded user-visible strings remain in thermalling settings UI paths.
- Dynamic thermalling labels use string-resource placeholders and locale-safe formatting.
- Repository behavior is covered by deterministic tests for defaults, clamping, and recovery.
- ViewModel has mapping/intent tests for all settings fields.
- Compose UI test verifies major setting interactions.
- String/localization verification evidence included in PR:
  - file list of moved strings (`strings.xml` entries)
  - `rg` check showing no remaining hard-coded thermalling UI copy in targeted files
- Commands pass:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

Phase 1 score uplift plan (target >= 9.3):

1. UI string extraction completion:
   - Move all thermalling UI copy to `app/src/main/res/values/strings.xml`.
   - Remove hard-coded labels from:
     - `ThermallingSettingsScreen.kt`
     - `Settings-df.kt` (Thermalling tile)
   - Evidence:
     - new string keys list in PR notes
     - `rg` evidence showing no hard-coded thermalling UI strings in those files
2. Locale-safe dynamic formatting:
   - Replace inline/interpolated labels with string-resource placeholders.
   - Replace non-locale-safe numeric formatting in thermalling UI.
   - Evidence:
     - code path references for placeholder-based labels (`enter`, `exit`, `zoom`, `range`)
3. DataStore recovery coverage:
   - Add repository read-failure recovery for `IOException` fallback to defaults.
   - Preserve non-IO exception propagation.
   - Evidence:
     - repository tests for `IOException` fallback, non-IO rethrow, post-recovery persistence
4. ViewModel + Compose coverage uplift:
   - ViewModel tests for all field mappings and setter intents.
   - Compose test for control enabled/disabled and persisted-state reflection.
   - Evidence:
     - test class/file list and passing test command output snippets in PR notes
5. Gate verification evidence:
   - Capture pass/fail and command lines for:
     - `python scripts/arch_gate.py`
     - `./gradlew enforceRules`
     - `./gradlew testDebugUnitTest`
     - `./gradlew assembleDebug`

Phase 1 score rubric:

- `>=9.3`: all five score-uplift items complete with evidence.
- `9.0 - 9.2`: one item incomplete or evidence partial.
- `<9.0`: two or more uplift items incomplete.

### Phase 2 - Coordinator Logic (production-ready upgrade)

Goal:

- Deliver a deterministic, SSOT-safe runtime coordinator that translates `isCircling` plus settings into explicit thermalling actions.

Implementation work packages:

1. Runtime contract and state model:
   - Add explicit runtime state model (`IDLE`, `ENTER_PENDING`, `ACTIVE`, `EXIT_PENDING`).
   - Define immutable session snapshot contract:
     - `preThermalMode`
     - `preThermalZoom`
     - `activeThermalZoom`
2. Coordinator API boundary:
   - Input API limited to domain-safe values (no Android/UI types).
   - Output as sealed action/events only (for mode switch, zoom apply, restore).
3. Deterministic timer logic:
   - Use injected monotonic clock only (`nowMonoMs()`).
   - Handle enter/exit timers, interrupted transitions, and re-entry without race conditions.
4. Policy rules:
   - Thermal mode availability gating.
   - Zoom-only fallback when thermal mode is hidden.
   - Manual zoom override behavior while in `ACTIVE`.
   - Runtime disable/reset behavior when settings toggle off.
5. Replay no-op guard (deferred by scope):
   - Live-only directive applied for this cycle; replay bypass/reset will be implemented during runtime wiring.
6. Telemetry hooks (non-authoritative):
   - Count transition episodes and blocked transitions (for QA and rollout diagnostics).

Planned files:

- `feature/map/src/main/java/com/trust3/xcpro/thermalling/ThermallingModeCoordinator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/thermalling/ThermallingModeState.kt`
- `feature/map/src/main/java/com/trust3/xcpro/thermalling/ThermallingModePolicy.kt`

Required test matrix:

- Enter timer:
  - `isCircling=true` sustained to threshold activates exactly once.
  - Broken circling before threshold returns to `IDLE` with no action.
- Exit timer:
  - `isCircling=false` sustained to threshold restores exactly once.
  - Circling resumes before threshold returns to `ACTIVE` without restore.
- Policy behavior:
  - Thermal mode hidden with fallback enabled applies zoom-only actions.
  - Thermal mode hidden with fallback disabled performs no mode/zoom switch.
- Session behavior:
  - Manual zoom override in `ACTIVE` is retained per setting.
  - Disable toggle mid-session clears pending state and stops future actions.
- Replay behavior (deferred):
  - Replay source no-op/reset test coverage will be added with runtime integration.
- Timebase:
  - Fake monotonic clock tests prove no wall-time dependency.

Phase 2 exit criteria (production-ready):

- Coordinator has no Android imports and no wall-time/system-time calls.
- All state transitions are covered by deterministic unit tests with fake clock/test dispatcher.
- Action stream is idempotent for equivalent input sequences.
- `python scripts/arch_gate.py` and `./gradlew :feature:map:testDebugUnitTest --tests "*ThermallingModeCoordinator*"` pass.

Phase 2 implementation update (2026-03-02, live-only production pass):

- Implemented runtime coordinator core:
  - `feature/map/src/main/java/com/trust3/xcpro/thermalling/ThermallingModeCoordinator.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/thermalling/ThermallingModeState.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/thermalling/ThermallingModePolicy.kt`
- Delivered state machine + deterministic timing:
  - phases `IDLE`, `ENTER_PENDING`, `ACTIVE`, `EXIT_PENDING`
  - injected monotonic clock (`Clock.nowMonoMs()`) only
  - enter/exit delays, interrupted transitions, restore/reset behavior
- Delivered live policy behavior:
  - thermal-hidden zoom-only fallback
  - policy-blocked bypass/reset
  - disable-mid-session restore/reset
  - manual thermal zoom memory inside active session
- Added deterministic unit coverage:
  - `feature/map/src/test/java/com/trust3/xcpro/thermalling/ThermallingModeCoordinatorTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/thermalling/ThermallingModePolicyTest.kt`

Verification evidence (2026-03-02):

- `python scripts/arch_gate.py` -> `ARCH GATE PASSED`
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.thermalling.ThermallingModePolicyTest" --tests "com.trust3.xcpro.thermalling.ThermallingModeCoordinatorTest"` -> `BUILD SUCCESSFUL`
- `./gradlew enforceRules` -> `Rule enforcement passed.`
- `./gradlew assembleDebug` -> `BUILD SUCCESSFUL`
- `./gradlew testDebugUnitTest` -> `FAILED` due to existing unrelated test:
  - `app/src/test/java/com/trust3/xcpro/profiles/ProfileRepositoryTest.kt`
  - failure: `ioReadError_preservesLastKnownGoodState`

Phase 2 score (live-only scope): `9.2 / 10`

- Strengths:
  - Coordinator architecture and state model are implemented and test-backed.
  - Timing/action logic is deterministic and clock-injected.
  - Policy/fallback/reset paths are implemented with dedicated tests.
- Deductions:
  - Replay no-op guard intentionally deferred by live-only scope.
  - Runtime orchestration into `MapScreenViewModel` is still pending (Phase 3).

Remaining production gaps after this Phase 2 pass:

1. Complete Phase 3 runtime wiring in `MapScreenViewModel` (single orchestration path from SSOT flows to coordinator actions).
2. Add replay no-op/reset integration once replay participation is re-enabled for thermalling automation.
3. Close global gate red status by resolving unrelated `:app:testDebugUnitTest` failure before release sign-off.

### Phase 3 - ViewModel Wiring (production-ready upgrade)

Goal:

- Integrate coordinator into map runtime without breaking MVVM/UDF/SSOT boundaries.

Implementation work packages:

1. Use-case integration:
   - Provide coordinator inputs from existing SSOT flows:
     - flight data (`isCircling`)
     - thermalling settings
     - replay/source mode
     - current mode/zoom state
2. VM orchestration:
   - Add a single ViewModel-owned orchestration path for coordinator actions.
   - Map coordinator output actions into existing mode/zoom intents only.
3. SSOT discipline:
   - Read mode/zoom from existing state owners only.
   - Do not add duplicate caches outside coordinator session snapshot.
4. User interaction coexistence:
   - Preserve manual mode switch behavior when feature disabled.
   - Preserve manual zoom interaction during active thermalling session per setting.
5. Failure-safety:
   - On coordinator reset/error path, default to no-op and keep manual control intact.

Planned files:

- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt` (if wrapper additions are required)
- Optional helper:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ThermallingModeRuntimeWiring.kt`

Required test matrix:

- VM action mapping:
  - Coordinator enter action triggers correct mode switch and zoom apply calls.
  - Coordinator exit action triggers configured restore calls.
- Source/feature gates:
  - Feature disabled -> no auto actions.
  - Replay source -> no auto actions.
- Availability/fallback:
  - Thermal mode unavailable path respects zoom fallback setting.
- Coexistence:
  - Manual mode/zoom remains functional and not overwritten outside coordinator actions.

Phase 3 exit criteria (production-ready):

- No business logic moved into Composables.
- `MapScreenViewModel` depends on use-cases/coordinator boundary only.
- VM integration tests pass for enter/exit/fallback/replay/disabled scenarios.
- Local debug run confirms no mode/zoom flicker in rapid circling transitions.

### Phase 4 - Release Hardening (production-ready upgrade)

Goal:

- Make the feature release-safe: UX-complete, observable, test-verified, and rollback-ready.

Implementation work packages:

1. UX completion and settings discoverability:
   - Ensure thermalling settings entry is consistent with proximity/settings IA.
   - Finalize icons, copy, and control ordering with existing settings patterns.
2. Localization/accessibility hardening:
   - All user-facing strings in resources.
   - TalkBack-friendly labels and state descriptions for switches/sliders.
3. Replay and mode-availability guardrails:
   - Verify runtime no-op policy for replay in integration tests.
   - Verify behavior when thermal mode is hidden in profile.
4. Observability and QA telemetry:
   - Add low-noise diagnostics counters for:
     - enter actions fired
     - exit actions fired
     - transitions blocked by policy/timer
   - Keep telemetry non-authoritative and debug-safe.
5. Documentation and pipeline sync:
   - Update `docs/ARCHITECTURE/PIPELINE.md` with coordinator wiring.
   - Update thermalling docs with final behavior and known limits.
6. Rollout and rollback protocol:
   - Stage 1: internal dogfood with `enabled=false` default.
   - Stage 2: controlled pilot opt-in validation.
   - Stage 3: production release after gate pass and QA sign-off.

Planned files:

- `feature/map/src/main/java/com/trust3/xcpro/navigation/SettingsRoutes.kt` (if IA tweaks needed)
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt`
- `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/General/THERMALLING/THERMALLING_MODE_FEATURE_SPEC.md`

Required test matrix:

- Navigation and IA route coverage for Thermalling settings entry points.
- End-to-end instrumentation smoke for enable/configure/use/disable loop.
- Replay session validation confirms no auto mode/zoom actions.
- Regression pass for manual mode switching and manual zoom.

Phase 4 exit criteria (production-ready):

- Required gates pass:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Relevant instrumentation gates pass on device/emulator:
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
- `PIPELINE.md` reflects final production wiring.
- Rollback path is documented and verified in PR notes.

## 5) Test Plan

- Unit tests:
  - Settings repository defaults + clamping + persistence + corruption fallback.
  - Coordinator state machine transitions (enter/exit timers, resume behavior).
  - VM wiring around mode/zoom snapshots and restore.
- Replay/regression tests:
  - Verify replay does not auto-switch/auto-zoom in v1.
- UI/instrumentation tests:
  - Settings screen controls persist values and restore from SSOT.
- Degraded/failure-mode tests:
  - Thermal mode hidden -> zoom-only fallback.
  - Rapid circling toggles around thresholds.
  - Settings disabled mid-session behavior.

Required checks:

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

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Mode thrash from noisy circling signal | poor UX | enter/exit timers + explicit state machine | XCPro Team |
| Zoom fights with user gestures | frustrating UX | apply once on enter; allow manual override during active session | XCPro Team |
| Hidden Thermal mode edge cases | inconsistent behavior | explicit fallback policy + tests | XCPro Team |
| Replay side effects | determinism/UX drift | explicit replay bypass + regression tests | XCPro Team |
| Settings data corruption edge case | feature reset / crash risk | DataStore recovery policy + repository tests | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership.
- Explicit time base in coordinator logic/tests.
- Replay determinism preserved (v1 bypass).
- Phase 1 production-ready exit criteria satisfied before Phase 2 merge.
- Phase 2 and Phase 3 production-ready exit criteria satisfied before Phase 4 release hardening sign-off.
- Phase 4 production-ready exit criteria satisfied before production rollout.
- `KNOWN_DEVIATIONS.md` unchanged unless approved exception is needed.

## 8) Rollback Plan

- Revert coordinator wiring in `MapScreenViewModel` (when introduced).
- Keep settings repository data harmless (`enabled=false` default).
- Remove route entry for Thermalling settings if necessary.
- Preserve key namespace to avoid preference collisions after rollback.
