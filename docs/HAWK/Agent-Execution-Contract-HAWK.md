
# Agent-Execution-Contract-HAWK.md

Purpose: single source of truth and single plan for implementing the HAWK
pipeline in XCPro.

Diagram: docs/HAWK/HAWKAGENT_PLAN.svg

References (read before coding):
- docs/ARCHITECTURE/ARCHITECTURE.md
- docs/ARCHITECTURE/CODING_RULES.md
- docs/ARCHITECTURE/PIPELINE.md


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
- Live fusion uses monotonic time.
- Repositories are SSOT owners; services are lifecycle hosts only.
- ViewModels follow the stable domain-facing seam policy in `docs/ARCHITECTURE/ARCHITECTURE.md`; no Android or persistence in ViewModels.
- UI renders state only and collects flows with lifecycle-aware APIs.
- Production Kotlin must be ASCII only. No vendor names in production strings.
- No literal "xcsoar" in production Kotlin source.

---

# 1) Change Request (PRE-FILLED FOR HAWK)

## 1.1 Feature Summary (1-3 sentences)
- Build a HAWK-like variometer pipeline that is fully separate from existing
  TE/flight calculations and can be removed later with minimal churn.
- HAWK is real-time only on phone sensors (barometer + IMU + GNSS as available).
  No IGC replay and no replay IMU in v1.

## 1.2 User Stories / Use Cases
- As a pilot, I want an optional HAWK-style vario output with low latency and
  fewer false lift spikes caused by phone pressure and handling artifacts.
- As a developer, I want HAWK isolated from the TE pipeline so it can be added
  or removed without touching existing calculations.
- As a tester, I want logging and tunable config so the HAWK feel can be
  validated without guessing.

## 1.3 Non-Goals (explicitly out of scope)
- No IGC replay support for HAWK in v1.
- No replay IMU or synthetic IMU generation.
- No raw gyro dependency in v1 (only if future evidence requires it).
- No changes to existing TE vario calculations or their outputs.
- No claims of true HAWK 3D wind or TAS; optional wind is out of scope.

## 1.4 Constraints
Platforms / modules:
- Android app, following existing feature/map architecture.
- HAWK code must be isolated (separate package or module) and removable.
Behavior constraints:
- Baro-clocked, baro-gated fusion: update only on new baro samples.
- Accel is a helper input that cannot create lift without baro support.
- QNH is for display altitude only; vario physics uses pressure altitude or QNE.
Performance / battery:
- Design for baro cadence and 100-200 Hz IMU; avoid heavy allocations.
Lifecycle:
- HAWK must run under VarioServiceManager (foreground service lifecycle).
Safety / compliance:
- Follow ARCHITECTURE.md and CODING_RULES.md without exceptions.

## 1.5 Inputs / Outputs
Inputs:
- Barometer pressure + monotonic timestamp.
- Earth-frame vertical acceleration + monotonic timestamp + reliability flag.
- Optional GNSS vertical speed for validation only (not as primary vario).
Outputs:
- HAWK vario outputs: v_raw, v_audio (smoothed), confidence.
- QC and debug metrics: innovation, accel variance, gating decisions.

## 1.6 Behavior Parity Checklist (refactor or replacements)
- Existing TE outputs and audio must not change when HAWK is disabled.
- Replay behavior and time base rules remain unchanged for TE.
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
  - Baro QC (median or Hampel filtering, dp/dt gates, innovation gating).
  - Adaptive accel trust from a short rolling accel variance/RMS window.
  - 2-state filter (h, v) with baro-gated steps only.
  - QNH decoupling: use pressure altitude/QNE in physics channel.
  - Output smoothing for v_audio with deadband/hysteresis.
- Use monotonic timestamps for dt and integrate only on new baro samples.
- Provide output model: HawkOutput (v_raw, v_audio, confidence, qcFlags, debug).
- Unit tests for filter stability, gating, and edge cases.

Gate: unit tests pass; core logic deterministic.

## Phase 2 -- Integration (Sensors + DI)
- Create a HAWK sensor adapter that consumes SensorDataSource or
  UnifiedSensorManager.
- Add a HAWK repository as SSOT for HawkOutput (parallel to FlightDataRepository).
- Wire via DI; keep HAWK separate from TE and fully removable.
- Add feature flag HAWK_ENABLED and tunable HawkConfig.
- Fallback modes:
  - accel unreliable or stale -> baro-only behavior.
  - baro missing -> hold output and decay confidence (no IMU-only stepping).

Gate: HAWK updates in debug build; TE unchanged.

## Phase 3 -- UI + Audio Wiring (Optional and Gated)
- Add a ViewModel/use-case path for HAWK output (no UI direct access).
- UI shows HAWK vario only when enabled; otherwise uses existing TE output.
- Audio: if enabled, use a separate HAWK audio path or a toggle in
  VarioAudioController without altering default TE behavior.
- Add regression tests for enable/disable state transitions.

Gate: feature works end-to-end when enabled; defaults unchanged.

## Phase 4 -- Hardening + Docs
- Handle missing sensors and background restrictions gracefully.
- Ensure foreground service usage is sufficient for continuous sensing.
- Add logging behind debug flags only (no release location logging).
- Add optional log capture format for tuning (baro + accel + reliability).
- Update docs/HAWK/Agent-Execution-Contract-HAWK.md and the diagram if wiring changes.

Gate: required checks pass; docs updated.

---

# 3) Acceptance Criteria

## 3.1 Functional
- Given HAWK disabled, existing TE outputs and audio are unchanged.
- Given HAWK enabled and baro+IMU present, HawkOutput updates on baro cadence.
- Given accel unreliable/stale, HAWK falls back to baro-only behavior.
- Given missing baro, HAWK outputs are paused/invalid but do not affect TE.
- Given QNH changes, HAWK does not create false climb/sink spikes.

## 3.2 Edge Cases
- No permissions or sensors: HAWK fails closed (no output) without crashes.
- Handling/rotation: no false lift spikes without baro support.
- Sensor jitter: adaptive accel trust and baro gating prevent pressure spikes
  from producing large false climbs.

## 3.3 Test Coverage Required
- Unit tests for QC filters, adaptive accel trust, and 2-state filter behavior.
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

- Decision: HAWK uses baro-gated fusion with adaptive accel trust and baro QC.
  Alternatives: baro-only differentiation. Rejected due to latency and
  susceptibility to pressure artifacts.
  Impact: requires accel variance tracking and innovation gating.

- Decision: No raw gyro dependency in v1. Use existing earth-frame vertical
  acceleration with reliability flag. Add gyro only if field evidence demands.
  Alternatives: add gyro now. Rejected due to plumbing/testing blast radius.
  Impact: simpler sensor contract, faster delivery.

- Decision: No IGC replay or replay IMU in v1.
  Alternatives: synthetic IMU for replay. Rejected due to misleading tuning.
  Impact: HAWK is real-time only; optional live log capture for tuning.

- Decision: QNH is display-only; vario physics uses pressure altitude/QNE.
  Alternatives: feed QNH-adjusted altitude into filter. Rejected due to
  step artifacts and false climb spikes.
  Impact: add step detection only if a QNH-coupled stream is unavoidable.

---

# 6) File Layout Guidance (Non-binding)

Suggested package/module layout to keep HAWK removable:

- feature/map/src/main/java/com/trust3/xcpro/hawk/
  - HawkSensorAdapter.kt
  - HawkVarioEngine.kt
  - HawkConfig.kt
  - HawkDebug.kt
  - BaroQc.kt
  - AdaptiveAccelNoise.kt
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

