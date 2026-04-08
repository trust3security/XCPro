
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

## 0.2 Responsibilities
- Implement the change described in **Section 1 (Change Request)**.
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
- Commit after each phase with a clear, scoped message.
- Do not leave TODOs or partial implementations in production paths.
- If an existing test must change, justify it strictly as behavior parity or updated requirements (cite Section 1).

## 0.4 Definition of Done
Work is complete only when:
- All phases in **Section 2 (Execution Plan)** are implemented.
- Tests cover the behavior checklist in **Section 3 (Acceptance Criteria)**.
- Required commands in **Section 4 (Required Checks)** pass.
- The change is documented in **Section 5 (Notes / ADR)** if new decisions were made.

---

# 1) Change Request (HUMAN FILLS THIS IN)

## 1.1 Feature Summary (1-3 sentences)
- [x] Refactor the cards ingestion pipeline so a single coordinator owns card updates, removing
  UI-side collectors and making cadence, hydration gating, and units propagation deterministic.
  Preserve current card values/behavior while improving maintainability and testability.

## 1.2 User Stories / Use Cases
- [x] As a pilot, I want cards to update smoothly and consistently, so that values are reliable in flight.
- [x] As a developer, I want a single ingestion path, so that the pipeline is easy to reason about and test.

## 1.3 Non-Goals (explicitly out of scope)
- [x] No changes to fusion math, filters, or vario algorithms.
- [x] No redesign of card UI layouts or visuals.
- [x] No replay pipeline changes beyond the card data handoff.

## 1.4 Constraints
- Platforms / modules:
  - [x] Android app modules: `feature/map`, `app` (tests), docs
  - [x] Libraries/layers: `dfcards-library`
- Performance / battery:
  - [x] Preserve current update cadence tiers; no added heavy polling.
- Backwards compatibility:
  - [x] Preserve existing CardPreferences, templates, and persisted positions.
- Safety / compliance:
  - [x] Keep timebase rules (monotonic for staleness, wall time only for display).

## 1.5 Inputs / Outputs
- Inputs:
  - [x] `CompleteFlightData` -> `RealTimeFlightData` via adapter
  - [x] `CardPreferences`, `UnitsPreferences`, `cardHydrationReady`
- Outputs:
  - [x] Card UI values updated through dfcards only.
  - [x] No new persistent storage beyond existing CardPreferences.
  - [x] Optional debug logs only if needed.

## 1.6 Behavior Parity" Checklist (if refactor or replacements)
- [x] Card values/labels/units match current behavior for the same inputs.
- [x] cardHydrationReady gating and buffered-sample behavior preserved (policy documented).
- [x] Profile/template selection and card positions persist unchanged.
- [x] FlightDataMgmt previews continue to render using liveFlightData.

---

# 2) Execution Plan (AGENT OWNS THIS, BUT MAY EDIT FOR REALITY)

## Phase Gate Protocol (MANDATORY)
For every phase below:
1) Implement only that phaseâ€™s scope.
2) Run the phaseâ€™s checks (see â€œGateâ€).
3) **If any check fails, fix and re-run until green.**
4) Only then proceed to the next phase.

Quality ladder:
- Phase 0â€“1: `./gradlew testDebugUnitTest`
- Phase 2â€“3: `./gradlew testDebugUnitTest` + `./gradlew lintDebug`
- Phase 4 (final): `./gradlew testDebugUnitTest` + `./gradlew lintDebug` + `./gradlew assembleDebug`

## Phase 0 -- Baseline & Safety Net
- Confirm current ingestion paths (MapComposeEffects + FlightDataMgmt) and hydration gate wiring.
- Lock behavior with baseline tests:
  - CompleteFlightData -> RealTimeFlightData mapping.
  - RealTimeFlightData -> card strings for a few key cards.
- Record current cadence (fast/primary tiers) and timebase behavior.
- Document decisions:
  - Null-data policy = freeze last values.
  - Units flow source = MapScreenViewModel StateFlow.
  - Buffered sample bucketing = keep raw (match current behavior).
  - No extra cardGridReady gate unless a regression is observed.
  - PrepareCardsForProfile refresh trigger after hydration.

**Gate:** no functional changes; `./gradlew testDebugUnitTest` green.

## Phase 1 -- Core Implementation
- Add `CardIngestionCoordinator` (MapScreenViewModel-owned, idempotent bind).
- Add a non-Compose units flow (MapScreenViewModel or FlightDataManager).
- Move initialization into the coordinator:
  - initializeCardPreferences
  - startIndependentClockTimer
  - units updates to dfcards
- Move ingestion into the coordinator:
  - collect cardFlightDataFlow
  - gate by cardHydrationReady + buffered sample
  - handle bind-time-ready case
  - apply chosen null-data policy
  - bucket buffered sample if required
- Add unit tests for coordinator behavior (idempotent bind, buffered sample, units, null-data).

**Gate:** `./gradlew testDebugUnitTest` green; behavior matches Section 3.

## Phase 2 -- Integration & UI Wiring
- Bind coordinator from both MapScreenRoot and FlightDataMgmt (idempotent).
- Remove UI collectors:
  - MapComposeEffects cardFlow/init/units
  - FlightDataMgmt cardFlow
- Keep prepareCardsForProfile in MapComposeEffects (profile/mode/size/density).
- Keep FlightDataMgmt previews on liveFlightData only.
- Update MapCardHydrationTest to use coordinator path.

**Gate:** `./gradlew testDebugUnitTest` + `./gradlew lintDebug` green; feature works end-to-end in debug build.

## Phase 3 -- Hardening
- Ensure lifecycle-safe cancellation and no duplicate collectors.
- Tighten hydration gating if fallback size causes early ingestion (optional).
- Remove dead code/unused helpers (if any).
- Update docs:
  - docs/flightdata.md
  - docs/RULES/PIPELINE.md + PIPELINE.svg
  - `docs/Cards/thermal_avg/TC30s.md` and `docs/Cards/netto_avg30/Netto30s.md` if they reference MapComposeEffects ingestion.
- Optional: confirm FlightDataProvider is unused; delete or mark legacy.

**Gate:** `./gradlew testDebugUnitTest` + `./gradlew lintDebug` green; docs updated and consistent.

## Phase 4 -- Polish & PR Readiness
- Run required checks:
  - ./gradlew testDebugUnitTest
  - ./gradlew lintDebug
  - ./gradlew assembleDebug
- Record ADR for decisions (null-data freeze, ingestion owner, buffering policy).
- Provide PR-ready summary and manual verification steps.

**Gate:** `./gradlew testDebugUnitTest` + `./gradlew lintDebug` + `./gradlew assembleDebug` green; Definition of Done satisfied.

---

# 3) Acceptance Criteria (HUMAN DEFINES, AGENT MUST SATISFY)

## 3.1 Functional Acceptance Criteria
- [ ] Given live flight data updates, when cards are hydrated, then cards update from exactly one ingestion path.
- [ ] Given the user opens FlightDataMgmt before Map, when the coordinator binds, then cards ingest and templates hydrate normally.
- [ ] Given cardHydrationReady transitions to true, when a buffered sample exists, then the buffered sample is applied once.
- [ ] Given units preferences change, when updates propagate, then dfcards values and units refresh without Compose collectors.
- [ ] Given live data becomes null (replay stop / sensor loss), then cards freeze last values (null-data policy).

## 3.2 Edge Cases
- [ ] Offline / no sensors: cards remain stable and do not crash; ingestion resumes on data return.
- [ ] Rotation / background: coordinator does not duplicate collectors and continues to deliver updates on return.
- [ ] Replay stop: cards freeze (do not clear) and resume on replay restart or live data.
- [ ] Fallback size: cardHydrationReady does not cause repeated buffered-sample replays.

## 3.3 Test Coverage Required
- [ ] Unit tests for coordinator logic (idempotent bind, buffered sample, units propagation, null-data policy).
- [ ] Update MapCardHydrationTest to use coordinator path.

---

# 4) Required Checks (AGENT MUST RUN AND PASS)

Minimum (edit to match your repo):
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`

Optional (if repo has them):
- `./gradlew detekt`
- `./gradlew ktlintCheck`
- `./gradlew connectedDebugAndroidTest`

Agent must report:
- Commands run
- Results (pass/fail)
- Any fixes applied

---

# 5) Notes / ADR (Architecture Decisions Record)

If any non-trivial decision is made, record it here:
- Decision: Null-data policy = freeze last values.
  Alternatives considered: clear to placeholders.
  Why chosen: preserves current behavior and avoids sudden UI resets.
  Impact / risks: stale values persist during data loss; acceptable for parity.
  Follow-ups: revisit if product wants explicit "no data" state.
- Decision: Units flow source = MapScreenViewModel StateFlow.
  Alternatives considered: expose flow from FlightDataManager.
  Why chosen: MapScreenViewModel already owns unitsUseCase and updates UI + FlightDataManager.
  Impact / risks: coordinator depends on MapScreenViewModel; fine given ownership.
  Follow-ups: none.
- Decision: Buffered sample bucketing = keep raw (no bucketing).
  Alternatives considered: bucket to match cardFlow output.
  Why chosen: preserves current first-sample behavior; avoids user-visible change.
  Impact / risks: first update may differ slightly from steady-state bucketed updates.
  Follow-ups: revisit after cadence measurements.
- Decision: No extra cardGridReady gate by default.
  Alternatives considered: add explicit cardGridReady signal from CardContainer.
  Why chosen: avoid behavioral change unless fallback size proves problematic.
  Impact / risks: early ingestion if fallback size triggers hydration; monitored in tests.
  Follow-ups: add gate if tests or QA reveal early-ingest issues.
- Decision: FlightDataManager construction moved behind a factory.
  Alternatives considered: direct ViewModel construction.
  Why chosen: keeps MapScreenViewModel focused on wiring and enables DI-friendly ownership.
  Impact / risks: minimal; factory is thin and uses existing injected dependencies.
  Follow-ups: optionally make factory an interface for testing.
- Decision: FlightDataUiAdapter wraps MapScreenObservers.
  Alternatives considered: rename MapScreenObservers directly or keep it in ViewModel.
  Why chosen: isolates conversion logic without large renames; keeps ViewModel thin.
  Impact / risks: minimal wrapper; MapScreenObservers remains single source of conversion.
  Follow-ups: rename MapScreenObservers to FlightDataUiAdapter if/when doc sweep is desired.
- Follow-on plan: docs/refactor/REFACTOR_ARCH_COMPLIANCE.md (architecture/coding_rules compliance refactor).

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


