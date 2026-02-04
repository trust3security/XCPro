# Agent-Execution-Contract-HAWK.md

Purpose: single source of truth and single plan for implementing the HAWK
pipeline in XCPro.

Diagram: docs/HAWK/HAWKAGENT_PLAN.svg

References (read before coding):
- docs/RULES/ARCHITECTURE.md
- docs/RULES/CODING_RULES.md
- docs/RULES/PIPELINE.md
- docs/HAWK/deep-research-reportPhonePlan.md
- docs/HAWK/deep-research-reportHAWK on Android.md
- docs/HAWK/deep-research-report-LXHAWK.md
- docs/HAWK/deep-research-report-LXHAWK-ONPhone.md

Single Plan Rule:
- This file is the ONLY plan for HAWK work.
- AGENT_RELEASE.md and TASK.md must reference it.
- Do not duplicate plan content elsewhere.

---

# 0) Agent Execution Contract (READ FIRST)

This document is the authoritative specification for this work.

The executing agent (Codex) is responsible for implementing the requested change
from start to finish as a self-directed software engineering task.

## 0.1 Authority
- Proceed through all phases without asking for confirmation.
- Do not ask questions unless blocked by genuinely missing information that
  cannot be inferred from the repository.
- If ambiguity exists, choose the most reasonable repo-consistent option and
  document the assumption in commits and/or PR notes.

## 0.2 Responsibilities
- Implement the change described in Section 1 (Change Request).
- Run builds, unit tests, and lint locally.
- Fix all build/test/lint failures encountered.
- Preserve existing user-visible behavior unless explicitly stated otherwise.
- Keep business logic pure and unit-testable (no Android framework calls in core
  logic unless the feature requires it).
- Prefer deterministic, injectable time sources:
  - Use monotonic time for staleness/elapsed logic.
  - Use wall time only for display timestamps.
  - Do not mix time sources in a single decision path.

## 0.3 Workflow Rules
- Work phase-by-phase, in order.
- Commit after each phase with a clear, scoped message.
- Do not leave TODOs or partial implementations in production paths.
- If an existing test must change, justify it strictly as behavior parity or
  updated requirements (cite Section 1).

## 0.4 Definition of Done
Work is complete only when:
- All phases in Section 2 (Execution Plan) are implemented.
- Tests cover the behavior checklist in Section 3 (Acceptance Criteria).
- Required commands in Section 4 (Required Checks) pass.
- The change is documented in Section 5 (Notes / ADR) if new decisions were made.

## 0.5 Project Rules (Non-negotiable)
- Architecture: MVVM + UDF + SSOT; DI via Hilt. UI -> domain -> data only.
- Domain/fusion must use injected clocks; no System.currentTimeMillis or
  SystemClock in domain or fusion.
- Live fusion uses monotonic time; replay uses IGC time.
- Repositories are SSOT owners; services are lifecycle hosts only.
- ViewModels depend on use-cases only; no Android or persistence in ViewModels.
- UI renders state only and collects flows with lifecycle-aware APIs.
- Production Kotlin must be ASCII only. No vendor names in production strings.
- No literal "xcsoar" in production Kotlin source.

---

# 1) Change Request (PRE-FILLED FOR HAWK)

## 1.1 Feature Summary (1-3 sentences)
- Build a HAWK-inspired variometer pipeline that is fully separate from existing
  TE/flight calculations and can be removed later with minimal churn.
- The HAWK pipeline consumes the existing sensor inputs (baro, IMU, GNSS, and
  optional rotation vector) but performs its own calculations and outputs.

## 1.2 User Stories / Use Cases
- As a pilot, I want an optional HAWK-style vario output with low latency and
  fewer false lift spikes caused by phone pressure artifacts.
- As a developer, I want HAWK to be isolated from the TE pipeline so it can be
  added or removed without touching existing calculations.
- As a tester, I want HAWK to work in both live sensors and replay mode with
  deterministic timing.

## 1.3 Non-Goals (explicitly out of scope)
- No attempt to replicate true HAWK instantaneous 3D wind estimation (phone has
  no dynamic pressure / TAS).
- No changes to existing TE vario calculations or their outputs.
- No claims of full HAWK gust immunity; only phone-appropriate artifact rejection.

## 1.4 Constraints
Platforms / modules:
- Android app, following existing feature/map architecture.
- HAWK code must be isolated (separate package or module) and removable.
Performance / battery:
- Design for 100-200 Hz IMU and baro cadence; avoid heavy allocations.
Backwards compatibility:
- Existing settings and TE behavior must remain unchanged by default.
Safety / compliance:
- Follow ARCHITECTURE.md and CODING_RULES.md without exceptions.

## 1.5 Inputs / Outputs
Inputs:
- Barometer (pressure), accelerometer, gyroscope, optional rotation vector.
- GNSS for optional wind estimate and logging.
- Monotonic timestamps from SensorEvent and Location.
Outputs:
- HAWK vario outputs (instant, display, avg), QC flags, confidence.
- Optional circling wind estimate (averaged) with confidence gating.
- Debug-only logs if enabled (no release logging of location).

## 1.6 Behavior Parity Checklist (refactor or replacements)
- Existing TE outputs and audio must not change when HAWK is disabled.
- Replay behavior and time base rules remain unchanged.
- No UI regression when HAWK feature is not enabled.

---

# 2) Execution Plan (HAWK-SPECIFIC)

## Phase 0 -- Baseline and Safety Net
- Locate sensor pipeline entry points (SensorRegistry, UnifiedSensorManager).
- Locate current fusion loop (FlightDataCalculatorEngine) and confirm HAWK will
  not alter it.
- Add a minimal HAWK module skeleton and unit-test scaffolding.
- Document current defaults and edge cases for missing sensors.

Gate: no functional changes; repo builds.

## Phase 1 -- Core Implementation (Pure Kotlin)
- Implement HAWK core as a separate package/module:
  - Pressure QC (median or Hampel filtering, dp/dt gates).
  - Vertical-channel EKF or complementary filter (state: h, v, ba).
  - Adaptive baro measurement noise (R) and innovation gating.
- Use monotonic timestamps for dt and integrate only on new samples.
- Provide output model: HawkOutput (varioInstant, varioDisplay, varioAvg,
  qcFlags, confidence).
- Unit tests for filter stability, gating, and edge cases.

Gate: unit tests pass; core logic deterministic.

## Phase 2 -- Integration (Sensors + DI)
- Create a HAWK sensor adapter that consumes SensorDataSource or UnifiedSensorManager.
- Add a HAWK repository as SSOT for HawkOutput (parallel to FlightDataRepository).
- Wire via DI, mirroring the WindSensorFusionRepository pattern for live/replay.
- Add settings or feature flag to enable HAWK without impacting TE pipeline.

Gate: HAWK updates in debug build; TE unchanged.

## Phase 3 -- UI + Audio Wiring (Optional and Gated)
- Add a ViewModel/use-case path for HAWK output (no UI direct access).
- UI shows HAWK vario only when enabled; otherwise uses existing TE output.
- Audio: if enabled, use a separate HAWK audio path or a toggle in
  VarioAudioController without altering default TE behavior.
- Add regression tests for UI state transitions.

Gate: feature works end-to-end when enabled; defaults unchanged.

## Phase 4 -- Hardening + Docs
- Handle missing sensors and background restrictions gracefully.
- Ensure foreground service usage is sufficient for continuous sensing.
- Add logging behind debug flags only.
- Update docs/HAWK/Agent-Execution-Contract-HAWK.md and the diagram if wiring changes.

Gate: required checks pass; docs updated.

---

# 3) Acceptance Criteria

## 3.1 Functional
- Given HAWK disabled, existing TE outputs and audio are unchanged.
- Given HAWK enabled and baro+IMU present, HawkOutput updates on baro cadence.
- Given replay mode, HAWK uses IGC time for dt and produces deterministic output.
- Given missing baro, HAWK outputs are invalid/paused but do not affect TE output.

## 3.2 Edge Cases
- No permissions or sensors: HAWK fails closed (no output) without crashes.
- Background: HAWK respects foreground service constraints (no stale loops).
- Sensor jitter: adaptive R and gating prevent pressure spikes from producing
  large false climbs.

## 3.3 Test Coverage Required
- Unit tests for QC filters and EKF/complementary filter behavior.
- Use-case tests for repository updates and confidence gating.
- VM tests for HAWK enable/disable state transitions (if UI wiring added).

---

# 4) Required Checks

Minimum:
- ./gradlew testDebugUnitTest
- ./gradlew lintDebug
- ./gradlew assembleDebug
- ./gradlew enforceRules

Optional (if already used in repo):
- ./gradlew detekt
- ./gradlew ktlintCheck
- ./gradlew connectedDebugAndroidTest

Agent must report:
- Commands run
- Results (pass/fail)
- Any fixes applied

---

# 5) Notes / ADR (Pre-Filled Decisions)

- Decision: HAWK pipeline is separate from existing TE pipeline and can be
  removed by deleting its package/module and DI wiring.
  Alternatives: integrate into FlightDataCalculatorEngine. Rejected to keep
  TE behavior untouched and simplify removal.
  Impact: parallel pipeline and repository wiring required.

- Decision: HAWK uses vertical-channel EKF with baro + IMU and adaptive gating.
  Alternatives: baro-only differentiation. Rejected due to latency and
  susceptibility to pressure artifacts.
  Impact: requires attitude estimation and QC.

- Decision: No claims of true HAWK 3D wind or TAS. Optional wind estimate is
  circling drift only, clearly labeled as averaged.
  Alternatives: attempt full wind triangle. Rejected due to missing airspeed.
  Impact: UI must label wind estimate with confidence.

---

# 6) File Layout Guidance (Non-binding)

Suggested package/module layout to keep HAWK removable:

- feature/map/src/main/java/com/example/xcpro/hawk/
  - HawkSensorAdapter.kt
  - HawkEngine.kt
  - HawkFilters.kt
  - HawkRepository.kt
  - HawkUseCase.kt
  - HawkOutput.kt

If a new Gradle module is acceptable, use:
- feature/hawk/ (pure Kotlin where possible)

---

# 7) Agent Output Format (MANDATORY)

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

