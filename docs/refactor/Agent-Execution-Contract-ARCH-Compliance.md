> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# AGENT.md -- Autonomous Engineering Execution Template

Use this file as the **single source of truth** when asking an agent (Codex) to implement a change end-to-end.
You (the human) fill in the Change Request" and any project-specific notes. The agent must follow the contract below.

---

# 0) Agent Execution Contract (READ FIRST)

This document is the authoritative specification for this work.

The executing agent (Codex) is responsible for implementing the requested change **from start to finish** as a self-directed software engineering task.

## 0.1 Authority
- Proceed through all phases **without asking for confirmation**.
- **Do not ask questions** unless blocked by genuinely missing information that cannot be inferred from the repository.
- If ambiguity exists, choose the most reasonable repo-consistent option and **document the assumption** in commits and/or PR notes.
- Treat `docs/refactor/REFACTOR_ARCH_COMPLIANCE.md` as the living inventory of violations.
  If new violations are found, append them there and continue without user interaction.
- Owner is **DFA** for any required exception records (use in `KNOWN_DEVIATIONS.md`).
- This contract must be executable **without user interaction**. If a decision is required,
  prefer the smallest behavior-preserving change that improves compliance,
  and record the assumption in Section 5 (Notes / ADR).
- Continue after failures; stopping early is forbidden unless execution is impossible.
- If rules conflict, follow the stricter interpretation.

## 0.2 Responsibilities
- Implement the change described in **Section 1 (Change Request)**.
- Mandatory reading order before edits:
  1) `docs/RULES/ARCHITECTURE.md`
  2) `docs/RULES/CODING_RULES.md`
  3) `docs/RULES/CONTRIBUTING.md`
  4) `docs/RULES/PIPELINE.md`
  5) `docs/RULES/KNOWN_DEVIATIONS.md`
- If touching variometer or replay logic, read `docs/LevoVario/levo.md` before edits.
- If touching map display pipeline, read `mapposition.md` before edits.
- Run builds, unit tests, and lint locally.
- Fix all build/test/lint failures encountered.
- Preserve existing user-visible behavior unless explicitly stated otherwise.
- Keep business logic pure and unit-testable (no Android framework calls in core logic unless the feature requires it).
- Prefer deterministic, injectable time sources:
  - Use **monotonic time** for staleness/elapsed logic.
  - Use **wall time** only for display timestamps.
  - Do not mix time sources in a single decision path.

## 0.3 Workflow Rules
- Work phase-by-phase, in order.
- Commit after each phase with a clear, scoped message **only if commits are explicitly requested**.
  Otherwise, log changes in Section 5 (Notes / ADR).
- Do not leave TODOs or partial implementations in production paths.
- If an existing test must change, justify it strictly as behavior parity or updated requirements (cite Section 1).
- After each phase, re-run the phase gate tests; fix failures before continuing.
- Phase progression rule (mandatory):
  - Run the phase gate tests.
  - If green, proceed to the next phase automatically.
  - If red, fix and re-run until green before moving on.
- Update the phase summary output (Section 6) at the end of every phase.

## 0.4 Definition of Done
Work is complete only when:
- All phases in **Section 2 (Execution Plan)** are implemented.
- Tests cover the behavior checklist in **Section 3 (Acceptance Criteria)**.
- Required commands in **Section 4 (Required Checks)** pass.
- The change is documented in **Section 5 (Notes / ADR)** if new decisions were made.

---

# 1) Change Request (HUMAN FILLS THIS IN)

## 1.1 Feature Summary (1-3 sentences)
- [x] Refactor the codebase to comply with `docs/RULES/ARCHITECTURE.md` and
  `docs/RULES/CODING_RULES.md` by enforcing DI boundaries, SSOT ownership,
  ViewModel purity, and injected timebases.
- [x] Preserve current user-visible behavior while removing architectural violations.

## 1.2 User Stories / Use Cases
- [x] As a developer, I want strict architecture boundaries so changes remain testable and safe.
- [x] As a pilot, I want the same behavior and performance with fewer hidden bugs.

## 1.3 Non-Goals (explicitly out of scope)
- [x] No UI redesigns or visual changes.
- [x] No changes to fusion math, scoring algorithms, or replay semantics.
- [x] No new persistence formats; reuse existing storage.

## 1.4 Constraints
- Platforms / modules:
  - [x] Android app module(s): `app`, `feature/map`, `feature/profile`
  - [x] Libraries / layers affected: `core/common`, `core/time`, `dfcards-library`
- Performance / battery:
  - [x] No added heavy polling; preserve existing cadence.
- Backwards compatibility:
  - [x] Preserve existing settings, templates, and persisted positions.
- Safety / compliance:
  - [x] Must comply with `docs/RULES/ARCHITECTURE.md` and `docs/RULES/CODING_RULES.md`.

## 1.5 Inputs / Outputs
- Inputs:
  - [x] Existing SharedPreferences and configuration.json content.
  - [x] Current task/waypoint/airspace files and repositories.
- Outputs:
  - [x] UI changes: none (behavior parity only).
  - [x] Data stored: no new formats; same storage locations.
  - [x] Logs / metrics: only debug logs if needed for parity.

## 1.6 Behavior Parity" Checklist (if refactor or replacements)
- [x] Cards, tasks, and navigation behavior remains unchanged for the same inputs.
- [x] Preferences and persisted data load identically.
- [x] Replay/live behavior unchanged aside from architecture cleanup.

## 1.7 Rule References (enforce in every phase)
- Architecture: `docs/RULES/ARCHITECTURE.md` (MVVM/UDF/SSOT/DI/timebase/viewmodel/UI rules).
- Coding rules: `docs/RULES/CODING_RULES.md` (timebase, DI, ViewModel purity, Compose lifecycle, SSOT).
- Exceptions must be recorded in `docs/RULES/KNOWN_DEVIATIONS.md` with owner + expiry.

---

# 2) Execution Plan (AGENT OWNS THIS, BUT MAY EDIT FOR REALITY)

## Progress Update (2026-02-03)
- Completed (Phase 1/2 items):
  - DocumentRef introduced and propagated for airspace/waypoints/task files/replay; Uri removed from ViewModel/use-case APIs.
  - GliderRepository singleton removed; DI binding + GliderUseCase + GliderViewModel; polar cards render ViewModel state only.
  - Direct ConfigurationRepository(context) calls removed; MapStyleRepository now injected; map style persistence via MapStyleUseCase.
  - Task files share/import/export now use DocumentRef and repository-owned Uri conversion.
- Remaining in Phase 2:
  - Remove MapLibre from TaskSheetViewModel; move map plotting to UI adapter.
  - Inject TaskSheetUseCase/TaskRepository (no internal construction).
  - Remove Context from MapScreenViewModel; replace concrete managers with use-case/facades.
  - Replace TaskFilesUseCase dependency on TaskManagerCoordinator.
- Next phase after Phase 2: Phase 3 (timebase compliance + fake-clock tests).

## Phase 0 -- Inventory + Baseline Tests
- Refresh all current violations in `docs/refactor/REFACTOR_ARCH_COMPLIANCE.md`.
- Add baseline unit tests for any time-dependent logic that will be refactored.
  If deterministic tests are impossible prior to Clock injection, stage them immediately
  after introducing fake clocks in Phase 3 (note the deferral in Section 5).
- Record any unavoidable exceptions in `docs/RULES/KNOWN_DEVIATIONS.md` (owner/expiry).
- Verify mandatory reading order (Section 0.2) completed; note any deviations in Section 5.

**Gate:** `./gradlew testDebugUnitTest` green.

## Phase 1 -- Preferences + UI Isolation
- Move UI SharedPreferences/file I/O into repositories + use-cases.
- Inject repositories into ViewModels; UI consumes only state.
- Remove duplicate FlightMgmt screen in `app/` or merge into the canonical one.
- Replace map widget SharedPreferences access with a repository/use-case + ViewModel
  (remove `MapUIWidgetManager` direct prefs usage).
- Move task file import/export/share into a repository + use-case + ViewModel;
  delete or repurpose `TaskFileOperations`/`TaskFileManagement`.
- Remove `ConfigurationRepository` construction from Compose (`MapComposeEffects`).
- Remove `ConfigurationRepository(context)` direct calls; route via `NavDrawerConfigUseCase`
  and related use-cases. Delete helper wrappers if unused after migration.
- Replace `GliderRepository.getInstance` + UI direct access with DI + `GliderUseCase`
  + `GliderViewModel`; update Polar cards to render ViewModel state only.

**Gate:** `./gradlew testDebugUnitTest` green; behavior parity confirmed.

## Phase 2 -- ViewModel Purity
- Remove Context from ViewModels; inject repositories/use-cases.
- Remove MapLibre dependencies from ViewModels; move to UI adapters.
- Ensure ViewModels depend on use-cases only and expose immutable StateFlow.
- Remove platform types from ViewModel and use-case APIs. Introduce a pure `DocumentRef`
  (string uri + displayName). Keep `Uri` strictly inside repositories and map at the boundary.
  Update Airspace/Waypoint/Task file flows end-to-end to use `DocumentRef`.
- Remove use-case internal repository construction (inject dependencies).
- Replace MapScreenViewModel dependencies on concrete managers with use-case/facade
  abstractions or move those managers behind UI adapters.

**Gate:** `./gradlew testDebugUnitTest` + `./gradlew lintDebug` green.

## Phase 3 -- Timebase Compliance
- Replace all domain/fusion SystemClock/System.currentTimeMillis with injected Clock.
- Add fake-clock tests for time-dependent logic (AAT, QNH, orientation).
- Ensure wall time is UI/output only.
- Targets include (at minimum):
  - AAT distance + interactive models + edit session time
  - AAT map coordinate converter
  - OrientationContracts defaults
  - QNH repository + use-case timestamps
  - VarioAudioEngine timers
  - OrientationDataSource timestamps
  - BallastController time source
- Remove duplicate OrientationClock; use `core/time/Clock`.

**Gate:** `./gradlew testDebugUnitTest` + `./gradlew lintDebug` green.

## Phase 4 -- Task Architecture Decoupling
- Split task domain (pure models/calcs) from MapLibre rendering adapters.
- Keep TaskManagerCoordinator pure; move map plotting to UI adapters.
- Move task persistence into a repository with injected storage.
- Remove MapLibre and Context from task coordinators/managers; UI holds map references.
- Route task UI intents through ViewModels (no direct TaskManagerCoordinator calls from Compose).

**Gate:** `./gradlew testDebugUnitTest` + `./gradlew lintDebug` green.

## Phase 5 -- SSOT Cleanup + Global State
- Remove duplicate parsers and duplicate persistence paths.
- Replace global mutable flags/caches with injected config or scoped caches.
- Update docs: SSOT ownership + dependency direction.
- Unify Waypoint parsing/ownership (single parser + single repository).
- Remove home waypoint persistence from `WaypointModels.kt` in favor of repository SSOT.
- Consolidate task persistence to one repository (no overlapping AAT/Racing stores).

**Gate:** `./gradlew testDebugUnitTest` + `./gradlew lintDebug` + `./gradlew assembleDebug` +
`./gradlew enforceRules` green (optionally `./gradlew detekt` + `./gradlew ktlintCheck`).

## Phase Execution Loop (Autonomous)
- For each phase (0->5): PLAN -> IMPLEMENT -> BUILD -> TEST -> FIX -> LOOP -> FINALIZE.
- Continue automatically through all phases until Definition of Done is satisfied.
- Stop only when all gates pass and Section 3 acceptance criteria are met.

---

# 3) Acceptance Criteria (HUMAN DEFINES, AGENT MUST SATISFY)

## 3.1 Functional Acceptance Criteria
- [ ] Given any UI screen, when it needs preferences, then it uses ViewModel state (no direct prefs).
- [ ] Given any ViewModel, when it needs data, then it depends on use-cases only (no Context/MapLibre).
- [ ] Given time-dependent logic, when it runs, then it uses injected Clock only.
- [ ] Given duplicated persistence/parsers, when refactor completes, then one SSOT owner remains.

## 3.2 Edge Cases
- [ ] Offline / no permissions / no sensors / empty data remain stable.
- [ ] Rotation / background / process death does not duplicate collectors or lose state.
- [ ] Errors are surfaced via state (no silent failures).

## 3.3 Test Coverage Required
- [ ] Unit tests for timebase-dependent logic using fake Clock.
- [ ] ViewModel tests for state transitions after repository injection.
- [ ] Task domain tests remain Android-free.

---

# 4) Required Checks (AGENT MUST RUN AND PASS)

Minimum (edit to match your repo):
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`
- `./gradlew enforceRules`

Optional (if repo has them):
- `./gradlew detekt`
- `./gradlew ktlintCheck`
- `./gradlew connectedDebugAndroidTest`

Agent must report:
- Commands run
- Results (pass/fail)
- Any fixes applied

**Autonomous rule:** After each phase, run the phase gate checks and proceed
to the next phase only if green. If any check fails, fix and re-run until green.

---

# 5) Notes / ADR (Architecture Decisions Record)

If any non-trivial decision is made, record it here:
- Decision:
- Alternatives considered:
- Why chosen:
- Impact / risks:
- Follow-ups:
- Decision: Baseline time-dependent tests beyond AAT calculator are deferred until Clock injection.
  Alternatives considered: write wall-time tests now.
  Why chosen: avoid flaky tests tied to System.currentTimeMillis/SystemClock.
  Impact / risks: coverage for some time-based paths lands in Phase 3.
  Follow-ups: add fake-clock tests for QNH, orientation, and audio in Phase 3.

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
- Final Done" checklist (Definition of Done items)
- PR-ready summary (what/why/how)
- How to verify manually (2-5 steps)


