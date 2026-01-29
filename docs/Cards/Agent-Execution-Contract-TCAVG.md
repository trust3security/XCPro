# AGENT.md — Autonomous Engineering Execution Template

Use this file as the **single source of truth** when asking an agent (Codex) to implement a change end-to-end.
You (the human) fill in the “Change Request” and any project-specific notes. The agent must follow the contract below.

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

## 1.1 Feature Summary (1–3 sentences)
- [x] Match XCSoar "TC Avg" semantics in XCPro for both live flight and replay.
- [x] Implement thermal validity and timing rules so the TC AVG card behaves identically to XCSoar.

## 1.2 User Stories / Use Cases
- [x] As a pilot, I want TC AVG to match XCSoar so I can compare performance consistently.
- [x] As a developer, I want replay behavior to mirror live behavior so I can validate TC AVG from IGC logs.

## 1.3 Non-Goals (explicitly out of scope)
- [x] Do not redesign the cards UI or change unrelated card behavior.
- [x] Do not introduce new user-facing settings unless required for parity.
- [x] Do not alter sensor fusion or vario sources outside TC AVG logic.

## 1.4 Constraints
- Platforms / modules:
  - [x] Android app modules: `feature/map` sensors + replay pipeline.
  - [x] Libraries / layers affected: `dfcards-library` (card mapping).
- Performance / battery:
  - [x] No additional background loops; changes must be O(1) per sensor sample.
- Backwards compatibility:
  - [x] Preserve existing settings and card ids; no migrations.
- Safety / compliance:
  - [x] Must comply with `ARCHITECTURE.md`, `CODING_RULES.md`, `CONTRIBUTING.md`, `README.md`.

## 1.5 Inputs / Outputs
- Inputs:
  - [x] Sensor fusion outputs (baro/TE altitude, GPS, circling state, flying state).
  - [x] Replay IGC pipeline (ReplaySensorSource -> SensorFusionRepository).
- Outputs:
  - [x] TC AVG card primary/secondary values and validity.
  - [x] Optional replay debug logs for TC AVG verification.
  - [x] No persistent data changes.

## 1.6 “Behavior Parity” Checklist (if refactor or replacements)
- [x] Circling detection thresholds: turn rate >= 4 deg/s, entry 15s, exit 10s.
- [x] Flying detection: ~10 m/s for 10s or AGL >= 300m.
- [x] TC Avg uses TE altitude gain over time in current thermal.
- [x] Last thermal only finalized if duration >= 45s and gain > 0m.
- [x] Thermal start/end timestamps align with first turning / first non-turning samples.
- [x] TC Avg invalid until a qualifying thermal exists.

---

# 2) Execution Plan (AGENT OWNS THIS, BUT MAY EDIT FOR REALITY)

## Phase 0 — Baseline & Safety Net
- Locate current TC AVG wiring and circling/flying gates.
- Confirm XCSoar parity references (TC Avg, circling, flying).
- Document XCPro defaults and edge cases in `docs/Cards/TCAVG.md`.
- Read and comply with `ARCHITECTURE.md`, `CODING_RULES.md`, `CONTRIBUTING.md`, and `README.md`.
- Follow enforceRules constraints (no non-ASCII in production Kotlin, no 'xcsoar' strings, no collectAsState without lifecycle, no direct system time in domain/fusion).

**Gate:** no functional changes; repo builds.

## Phase 1 — Core Implementation
- Add XCSoar thermal validity gates (>=45s, gain>0) in `ThermalTracker`.
- Align thermal start/end timing to turning state (pre-hysteresis).
- Keep logic in domain layer (no Android dependencies).
- Add unit tests for thermal qualification and timing.

**Gate:** unit tests pass; behavior matches Section 3.

## Phase 2 — Integration & UI Wiring
- Wire validity flags into `FlightCalculationHelpers` and `CalculateFlightMetricsUseCase`.
- Ensure `RealTimeFlightData.currentThermalValid` matches XCSoar semantics.
- No UI redesign; only card value validity changes.

**Gate:** feature works end-to-end in debug build.

## Phase 3 — Hardening
- Add optional replay log fields for TC AVG verification.
- Update docs to reflect final parity rules.

**Gate:** all required checks pass (Section 4).

## Phase 4 — Polish & PR Readiness
- Ensure minimal diff, clean formatting.
- Provide PR summary and test results.

**Gate:** Definition of Done satisfied.

---

# 3) Acceptance Criteria (HUMAN DEFINES, AGENT MUST SATISFY)

## 3.1 Functional Acceptance Criteria
- [x] Given circling < 45s, when circling ends, then TC Avg remains invalid (no last thermal).
- [x] Given circling >= 45s and gain > 0, when circling ends, then TC Avg shows last thermal average.
- [x] Given a prior valid last thermal, when a short/negative thermal occurs, then TC Avg keeps the prior last thermal.
- [x] Given circling state, TC Avg uses current thermal average based on TE altitude gain over time.

## 3.2 Edge Cases
- [x] Replay IGC with straight flight: TC Avg stays invalid.
- [x] Replay IGC with intermittent turning: TC Avg only valid after qualifying thermal.
- [x] Missing/invalid TE altitude: thermal tracking is skipped safely.

## 3.3 Test Coverage Required
- [x] Unit tests for `ThermalTracker` qualification and timing.
- [x] Unit tests for current/last thermal validity behavior.

---

# 4) Required Checks (AGENT MUST RUN AND PASS)

Minimum (best practice, repo standard):
- `preflight.bat`

Recommended when UI/Compose/resources change:
- `./gradlew lintDebug`

Optional (run if CI or scope warrants):
- `gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
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
- Decision:
- Alternatives considered:
- Why chosen:
- Impact / risks:
- Follow-ups:

Expected decisions:
- Thermal validity gate (>=45s, gain>0) mirrors XCSoar.
- Thermal start/end aligned to turning (pre-hysteresis).

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
- Final “Done” checklist (Definition of Done items)
- PR-ready summary (what/why/how)
- How to verify manually (2–5 steps)
