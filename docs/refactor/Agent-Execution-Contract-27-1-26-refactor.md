# AGENT.md - Autonomous Engineering Execution Template

This file is based on `Agent-Execution-Contract.md` and tailored for the 2026-01-27 compliance refactor.
Use it as the single source of truth for any agent working this refactor end to end.

---

# Status Update (2026-01-28)

- Phase 2B (ViewModel purity) complete.
- Phase 3A (lifecycle collection) complete.
- Phase 3B (vendor neutrality and ASCII) complete.
- Phase 1 test coverage (FakeClock + timebase test) complete.
- Phase 2A test/fake binding for manager construction complete.
- All phases (0-4) complete; maintenance only.
- `./gradlew testDebugUnitTest` PASS (re-run after MapOverlayWidgetGesturesTest moved to Robolectric).
- `./gradlew enforceRules` PASS.
- `KNOWN_DEVIATIONS.md` now shows no open deviations.

# Next Actions

- Maintenance: add owner/expiry dates to any future deviations and run `preflight.bat` before major refactors.
- Required checks complete (lintDebug, assembleDebug, enforce_rules.ps1 via Windows PowerShell, preflight.bat).

# Required Checks - Current Status

- PASS: `./gradlew testDebugUnitTest`
- PASS: `./gradlew enforceRules`
- PASS: `./gradlew lintDebug`
- PASS: `./gradlew assembleDebug`
- PASS: `powershell -File scripts/ci/enforce_rules.ps1` (pwsh not installed)
- PASS: `preflight.bat`

# 0) Agent Execution Contract (READ FIRST)

This document is the authoritative specification for this work.

The executing agent (Codex) is responsible for implementing the requested change from start to finish as a self-directed software engineering task.

## 0.1 Authority
- Proceed through all phases without asking for confirmation.
- Do not ask questions unless blocked by genuinely missing information that cannot be inferred from the repository.
- If ambiguity exists, choose the most reasonable repo-consistent option and document the assumption in commits and/or PR notes.

## 0.2 Responsibilities
- Implement the change described in Section 1 (Change Request).
- Run builds, unit tests, and lint locally.
- Fix all build/test/lint failures encountered.
- Preserve existing user-visible behavior unless explicitly stated otherwise.
- Keep business logic pure and unit-testable (no Android framework calls in core logic unless the feature requires it).
- Prefer deterministic, injectable time sources:
  - Use monotonic time for staleness/elapsed logic.
  - Use wall time only for display timestamps.
  - Do not mix time sources in a single decision path.

## 0.3 Workflow Rules
- Work phase-by-phase, in order.
- Commit after each phase with a clear, scoped message.
- Do not leave TODOs or partial implementations in production paths.
- If an existing test must change, justify it strictly as behavior parity or updated requirements (cite Section 1).

## 0.4 Definition of Done
Work is complete only when:
- All phases in Section 2 (Execution Plan) are implemented.
- Tests cover the behavior checklist in Section 3 (Acceptance Criteria).
- Required commands in Section 4 (Required Checks) pass.
- The change is documented in Section 5 (Notes / ADR) if new decisions were made.

---

# 1) Change Request (FILLED)

## 1.1 Feature Summary (1-3 sentences)
Bring the repo into compliance with `ARCHITECTURE.md` and `CODING_RULES.md` using the refactor checklist in `docs/COMPLIANCE_PLAN.md`.
Target all documented deviations from the 2026-01-27 audit and remove the need for `KNOWN_DEVIATIONS.md` entries where possible.

## 1.2 User Stories / Use Cases
- As a developer, I want deterministic replay and fusion results so that the same IGC yields identical outputs.
- As a maintainer, I want DI-enforced construction so that testable fakes can replace pipeline components.
- As a reviewer, I want UI and ViewModel purity so architectural boundaries remain clear.

## 1.3 Non-Goals (explicitly out of scope)
- Feature expansion or UI redesign.
- Behavior changes beyond what is required to satisfy architecture rules.
- Performance tuning unrelated to compliance.

## 1.4 Constraints
- Platforms / modules:
  - Android app modules: `app`, `feature/map`, `feature/variometer`, `feature/profile`, `core`, `dfcards-library`.
  - Libraries / layers affected: repositories, use-cases, ViewModels, and Compose UI.
- Performance / battery:
  - No additional long-running loops; no new polling.
- Backwards compatibility:
  - Preserve existing preferences and stored values.
- Safety / compliance:
  - Follow `ARCHITECTURE.md` and `CODING_RULES.md` strictly.
  - All deviations must be removed or explicitly documented with owner/issue/expiry.

## 1.5 Inputs / Outputs
- Inputs:
  - Sensor streams, replay IGC, user preferences, task files.
- Outputs:
  - UI remains visually consistent.
  - Data stored in existing locations without schema changes.
  - Logs remain minimal and not required for correctness.

## 1.6 Behavior Parity Checklist (refactor)
- Replay results must be deterministic across runs.
- Live sensor fusion math should behave the same within tolerance.
- UI state and navigation behavior must not change.

---

# 2) Execution Plan (AGENT OWNS THIS)

## Phase 0 - Baseline and Safety Net
- Map all timebase usages and DI construction points.
- Add or confirm tests to lock current behavior for replay and timing.
- Document current defaults and edge cases.

Gate: no functional changes; repo builds.

## Phase 1 - Timebase
- Add Clock interface with nowMonoMs() and nowWallMs().
- Add Hilt bindings and a FakeClock for tests.
- Inject Clock into fusion pipeline constructors and replace System.currentTimeMillis/SystemClock usages in domain and fusion paths.
- Update or add determinism/timebase tests.

Gate: unit tests pass; replay determinism confirmed.

## Phase 2A - Dependency Injection
- Provide SensorFusionRepository via Hilt.
- Refactor VarioServiceManager to use injected SensorFusionRepository.
- Refactor replay pipeline construction to be DI-managed.

Gate: DI graph builds; pipeline constructed only via DI.

## Phase 2B - ViewModel Purity
- Move SharedPreferences access out of ViewModels.
- Remove Compose/UI types from ViewModels.
- Ensure ViewModels depend on use-cases only.
- Update ViewModel tests with fakes.

Gate: ViewModels are platform-agnostic; tests pass.

## Phase 3A - Lifecycle Collection
- Replace collectAsState() with collectAsStateWithLifecycle() in Compose.
- Ensure non-Compose collection uses repeatOnLifecycle or equivalent.

Gate: UI state collection is lifecycle-aware.

## Phase 3B - Vendor Neutrality and ASCII
- Remove vendor strings (xcsoar/XCSoar) from production Kotlin source (identifiers and runtime strings).
- Replace non-ASCII characters in production Kotlin source.

Gate: CI rules pass for vendor strings and ASCII.

## Phase 4 - Polish and PR Readiness
- Ensure code style, readability, and minimal diffs.
- Update docs and `KNOWN_DEVIATIONS.md` as needed.
- Provide PR summary and testing evidence.

Gate: Definition of Done satisfied.

---

# 3) Acceptance Criteria (HUMAN DEFINES, AGENT MUST SATISFY)

## 3.1 Functional Acceptance Criteria
- No System.currentTimeMillis/SystemClock/Date/Instant.now in domain or fusion logic.
- All core pipeline components are injected (no manual construction in managers/VMs).
- ViewModels do not reference SharedPreferences or Compose UI types.
- Compose UI uses collectAsStateWithLifecycle for state collection.
- No vendor strings (xcsoar/XCSoar) in production Kotlin source.
- No non-ASCII characters in production Kotlin source.

## 3.2 Edge Cases
- Replay determinism: same IGC replay yields identical output.
- Timebase: no wall time usage in replay decisions.
- Lifecycle: background/foreground does not leak collectors.

## 3.3 Test Coverage Required
- Unit tests for timebase and replay determinism.
- VM tests for state transitions where refactors occurred.

---

# 4) Required Checks (AGENT MUST RUN AND PASS)

Minimum:
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`

Repo-specific:
- `pwsh scripts/ci/enforce_rules.ps1`
- `gradlew enforceRules`
- `preflight.bat`

Agent must report:
- Commands run
- Results (pass/fail)
- Any fixes applied

---

# 5) Notes / ADR (Architecture Decisions Record)

## Evidence of Non-Compliance (scan 2026-01-27)

Timebase and DI deviations (#1, #2) are resolved per `KNOWN_DEVIATIONS.md`. The list below captures the 2026-01-27 scan findings (now resolved by Phase 2B/3A/3B unless noted).

### ViewModel purity (UI types, SharedPreferences, repositories)
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt:5` (Compose UI type in VM)
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt:151` (SharedPreferences in VM)
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt:4-7` (Compose types in VM)
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt:42` (constructs CardStateRepository)
- `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsViewModel.kt:29` (repository flow in VM)
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/LevoVarioSettingsViewModel.kt:25,30,60` (repo + pipeline access in VM)
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OrientationSettingsViewModel.kt:26` (repository in VM)
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskSheetViewModel.kt:24` (constructs TaskRepository)
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt:22` (repository in VM)

### UI accessing repositories / business logic in Composables
- `feature/map/src/main/java/com/example/xcpro/screens/airspace/AirspaceSettingsScreen.kt:46` (AirspaceRepository in UI)
- `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataScreensTab.kt:43-51` (UnitsRepository in UI + collectAsState)
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/PolarConfigCard.kt:37` (GliderRepository in UI)
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/UnitsSettings.kt:61-63` (UnitsRepository in UI)
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Profiles.kt:41-56` (ConfigurationRepository in UI)
- Additional `AirspaceRepository` usage across navdrawer/task screens (see `rg -n "AirspaceRepository" feature/map/src/main/java/com/example/xcpro/screens`)

### Lifecycle collection (collectAsState instead of collectAsStateWithLifecycle)
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardContainer.kt:60,62,115`
- `feature/map/src/main/java/com/example/xcpro/screens/replay/IgcReplayScreen.kt:50`
- `feature/map/src/main/java/com/example/xcpro/navdrawer/DrawerMenuSections.kt:40`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt:279`
- `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataScreensTab.kt:45-51`
- `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataMgmt.kt:152-153`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/LevoVarioSettingsScreen.kt:41`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/UnitsSettings.kt:63`
- Additional occurrences remain (see `rg -n "collectAsState" feature dfcards-library`)

### Vendor neutrality (xcsoar strings in production Kotlin)
- Identifier usage: `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt:84-86`,
  `feature/map/src/main/java/com/example/xcpro/sensors/SensorData.kt:129-131`,
  `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt:114`,
  `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:401-402`
- UI strings: `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/LevoVarioSettingsScreen.kt:95,97,205`,
  `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/VarioAudioComponents.kt:160,182,201`,
  `feature/map/src/main/java/com/example/xcpro/tasks/racing/ui/RacingTurnPointSelector.kt:221`
- Comments in production Kotlin also include XCSoar references (see `rg -n "XCSoar" feature dfcards-library -g "**/src/main/**/*.kt"`)

### ASCII-only production Kotlin
- Non-ASCII characters appear widely in `src/main` (emoji, bullets, degree symbols, arrows, smart quotes).
- Examples:
  - `app/src/main/java/com/example/xcpro/MainActivityScreen.kt:94-100` (emoji in logs)
  - `core/common/src/main/java/com/example/xcpro/common/waypoint/WaypointModels.kt:45-52` (emoji)
  - `dfcards-library/src/main/java/com/example/xcpro/common/units/UnitsPreferences.kt:118-119` (degree symbol)
  - `feature/map/src/main/java/com/example/xcpro/tasks/aat/rendering/AATFeatureFactory.kt:27,70,98,116,128` (emoji/arrows)
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileQuickActions.kt:156` (bullet symbol)
- Full list: `rg -n --pcre2 "[^\\x00-\\x7F]" feature app core dfcards-library -g "**/src/main/**/*.kt"`

## Resolution Notes (2026-01-27)

- Phase 2B/3A/3B items above are now resolved in code.
- Remaining work is complete; maintenance only.

---

# 6) Agent Output Format (MANDATORY)

At the end of each phase, the agent outputs:

## Phase N Summary
- What changed:
- Files touched:
- Tests run:
- Results:
- Next:

At the end of the task, include:
- Final Done checklist (Definition of Done items)
- PR-ready summary (what/why/how)
- How to verify manually (2-5 steps)
