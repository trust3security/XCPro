> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# Agent-Execution-Contract-Netto.md

This document is the execution plan and contract for implementing the phone-only
Wind / TAS proxy / IAS proxy / TE gating / Netto tiering / Confidence upgrades.

Authoritative references (read in this order):
1) ARCHITECTURE.md
2) CODING_RULES.md
3) docs/CODEBASE_CONTEXT_AND_INTENT.md
4) docs/GENIUS_PHONE_SENSORS_WIND_TAS_IAS_NETTO_SPEC.md
5) docs/CONFIDENCE_MODEL_SPEC.md
6) docs/NETTO_SETTINGS_S100_PARITY.md
7) docs/PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md
8) docs/GOLDEN_REPLAY_TEST_PLAN.md

If any implementation conflicts with these docs, the implementation is wrong.

-------------------------------------------------------------------------------

0) Agent Execution Contract (READ FIRST)

0.1 Authority
- Proceed through phases in order without asking for confirmation.
- Ask questions only if blocked by missing repo info that cannot be inferred.
- If ambiguity exists, choose the most reasonable repo-consistent option and
  document the assumption in this file and in the PR summary.

0.2 Responsibilities
- Implement the change described in Section 1.
- Keep SSOT ownership, MVVM/UDF, and DI rules intact.
- Use injected time sources only:
  - Monotonic time for elapsed/age/staleness in live mode.
  - IGC/replay time for replay mode.
  - Wall time only for UI labels or persistence timestamps.
- Keep business logic pure and unit-testable (no Android framework in domain).
- Centralize thresholds in a single domain constants file.

0.3 Workflow Rules
- Work phase-by-phase in order.
- Add tests before or with behavior changes.
- No TODOs in production paths.
- Keep KNOWN_DEVIATIONS.md empty.

0.4 Definition of Done
- All phases complete with tests.
- Required checks pass (Section 4).
- Behavior matches specs exactly.

-------------------------------------------------------------------------------

1) Change Request (FILLED)

1.1 Feature Summary (1-3 sentences)
Implement the phone-only Wind/TAS/IAS/TE/Netto upgrades with explicit confidence
and tiering, strict timebase handling, and deterministic replay gates. The goal
is to deliver stable, honest netto behavior and prevent confidently-wrong outputs
while preserving the existing MVVM/UDF/SSOT architecture.

1.2 User Stories / Use Cases
- As a pilot, I want netto to appear only when it is trustworthy, so I can rely
  on it for decision making.
- As a pilot, I want TAS/IAS to be clearly valid/degraded/invalid, so I know when
  to trust the numbers.
- As a developer, I want deterministic replay gates, so changes cannot silently
  degrade TE/netto behavior.

1.3 Non-Goals (explicitly out of scope)
- No new sensor fusion algorithms beyond the circling wind method.
- No ground-speed substitution for TAS/IAS or netto.
- No UI-driven computation or SSOT violations.
- No changes to unrelated features (tasks, maps, unrelated cards).

1.4 Constraints
- Architecture:
  - MVVM + UDF + SSOT with DI (ARCHITECTURE.md).
  - Domain must not call SystemClock/System.currentTimeMillis (CODING_RULES.md).
- Timebase:
  - Live: monotonic; Replay: IGC; Wall time: UI only.
- Determinism:
  - Replay must be deterministic (GOLDEN_REPLAY_TEST_PLAN.md).
- Encoding:
  - ASCII-only production Kotlin and docs (CODING_RULES.md).

1.5 Inputs / Outputs
- Inputs:
  - GPS, baro/vario, wind state, circling state, airspeed sources, replay clock.
- Outputs:
  - Domain: wind quality, airspeed validity/tier, TE gating, netto validity/tier.
  - UI: render-only indicators for TAS/IAS validity and tier.

1.6 Behavior Parity Checklist (must remain identical)
- Wind vector convention ("wind TO"; air = ground - wind_to).
- SSOT ownership (wind from repository, metrics from domain use case).
- Deterministic replay timebase usage.

-------------------------------------------------------------------------------

2) Execution Plan (DETAILED)

Phase 0 - Baseline and Safety Net (no behavior changes)
Goal: Map the current pipeline and lock current behavior where needed.
Reference: PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md Phase 0.

Tasks:
- Locate current wind storage/flow and how it reaches CalculateFlightMetricsUseCase.
- Locate airspeed estimation path (WindEstimator, AirspeedEstimate, SensorFrontEnd).
- Identify all netto-related outputs and UI wiring (FlightMetricsResult, CompleteFlightData, UI).
- Create docs/WIND_PIPELINE_MAP.md with:
  - Flow diagram (sensor -> repo -> use case -> viewmodel -> UI).
  - List of touch points (files + responsibilities).
- Add characterization tests if any behavior needs a baseline before change.

Gate:
- No functional changes.
- Repo builds clean.

Phase 1 - Explicit quality/validity models (domain-only)
Goal: Make truthiness explicit and deterministic.
References: CONFIDENCE_MODEL_SPEC.md, PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md Phase 1.

Tasks:
- Add domain models:
  - AirspeedTier { A, B, C }
  - AirspeedValidity(tasValid, iasValid, tier, reason)
  - WindQuality(score, ageSeconds, isStale, reason)
- Add confidence-related data classes from CONFIDENCE_MODEL_SPEC.md.
- Centralize thresholds in a new domain constants file (FlightMetricsThresholds.kt).
- Extend FlightMetricsResult to carry:
  - windQualityScore, windAgeSeconds
  - tasValid, iasValid
  - airspeedTier (ASCII string), airspeedReason (ASCII)
  - any required confidence scores/grades
- Add JVM tests for tiering logic (wind score/age -> TAS/IAS validity).

Gate:
- Unit tests pass.
- No Android dependencies in domain.

Phase 2 - Wind persistence and staleness (repo + domain contract)
Goal: Persist wind, degrade via confidence, no sudden drop to zero.
References: GENIUS_PHONE_SENSORS_WIND_TAS_IAS_NETTO_SPEC.md,
PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md Phase 2.

Tasks:
- Update wind repository/store to persist:
  - vector, timestamp, altitude, quality score, source.
- Compute age using injected clock:
  - live: monotonic
  - replay: IGC time
- Ensure WindState exposes age + quality score + stale flag.
- Ensure CalculateFlightMetricsUseCase receives WindState from repository only.
- Add tests:
  - wind persists across simulated time.
  - wind becomes stale but does not drop to zero.
  - replay time regressions handled safely.

Gate:
- Unit tests pass.
- No wall time in domain.

Phase 3 - Freeze wind learning during glide; hold wind through turns
Goal: Circling-only wind updates and held wind outside circles.
References: GENIUS_PHONE_SENSORS_WIND_TAS_IAS_NETTO_SPEC.md,
PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md Phase 3.

Tasks:
- Disable straight-flight wind estimation (EKF) when it relies on derived TAS.
- Update wind fusion pipeline to accept only circling wind for AUTO.
- Keep manual/external wind overrides unchanged.
- Add tests:
  - glide -> circle -> glide sequences update wind only in circle.
  - wind remains constant through glide.

Gate:
- Unit tests pass.
- Wind does not change outside circling.

Phase 4 - TAS proxy gating and hold-through-turns
Goal: TAS/IAS only when wind is valid; hold during circling.
References: GENIUS_PHONE_SENSORS_WIND_TAS_IAS_NETTO_SPEC.md,
CONFIDENCE_MODEL_SPEC.md, PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md Phase 4.

Tasks:
- Compute TAS proxy only when wind quality >= MEDIUM and wind age <= max age.
- Implement TAS hold while circling (do not recompute during turns).
- IAS proxy exists only when TAS valid.
- Do not substitute ground speed for TAS/IAS.
- Update AirspeedEstimate selection and SensorFrontEnd to respect validity/hold.
- Add tests:
  - circling noise does not change TAS.
  - wind invalid -> TAS/IAS invalid.
  - stale wind -> tier B.

Gate:
- Unit tests pass.
- TAS/IAS validity matches spec.

Phase 5 - TE gating (no TE without TAS)
Goal: TE only computed when TAS valid.
References: GENIUS_PHONE_SENSORS_WIND_TAS_IAS_NETTO_SPEC.md,
PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md Phase 5.

Tasks:
- Update CalculateFlightMetricsUseCase TE logic to require tasValid.
- Ensure TE is never computed from ground speed fallback.
- Add tests:
  - TAS invalid -> TE null and vario falls back to pressure/baro.
  - TAS valid -> TE computed and used.

Gate:
- Unit tests pass.

Phase 6 - Netto tiering and S100 settings
Goal: Tiered netto behavior with pilot-facing settings.
References: NETTO_SETTINGS_S100_PARITY.md,
GENIUS_PHONE_SENSORS_WIND_TAS_IAS_NETTO_SPEC.md,
PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md Phase 6.

Tasks:
- Add settings keys (netto_avg_seconds, netto_mode, te_policy, airspeed_display, quality_badge).
- Implement netto tiering:
  - Tier A: normal, 5s or 10s averaging.
  - Tier B: degraded, force >= 10s, marked degraded.
  - Tier C: invalid/hidden.
- Keep 30s netto average for existing cards (do not change card semantics).
- Domain owns tiering and validity; UI only smooths visuals.
- Add tests:
  - Tier C hides netto.
  - Tier B never reports Tier A validity.
  - Tier A respects selected averaging.

Gate:
- Unit tests pass.
- UI does not own SSOT values.

Phase 7 - UI indicators (non-authoritative)
Goal: Render tier/validity indicators without changing SSOT.
References: NETTO_SETTINGS_S100_PARITY.md,
CODEBASE_CONTEXT_AND_INTENT.md, PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md Phase 7.

Tasks:
- Surface airspeed tier and reasons in UI state.
- Render TAS/IAS values with validity gating and quality badge.
- No UI computation of domain values.
- Add UI or ViewModel tests for state rendering.

Gate:
- UI displays correct tier/labels.

Phase 8 - Deterministic replay gate
Goal: End-to-end determinism and anti-flicker gates.
References: GOLDEN_REPLAY_TEST_PLAN.md,
PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md Phase 8.

Tasks:
- Add golden IGC fixtures (or synthetic) under
  feature/map/src/test/resources/igc/golden/
- Build JVM-only replay runner and record per-tick outputs:
  wind, TAS/IAS validity, TE on/off, netto tier/confidence.
- Determinism gate: replay twice with identical outputs.
- Stability gates: tier oscillation, TE flicker, wind freeze, netto honesty.

Gate:
- Determinism and stability tests pass locally and in CI.

-------------------------------------------------------------------------------

3) Acceptance Criteria

3.1 Functional
- TAS/IAS are valid only when wind quality is MEDIUM+ and wind age is below max.
- TE is computed only when TAS is valid (never from ground speed).
- Netto tiers A/B/C are deterministic and stable; Tier C hides netto.
- Wind does not change during glide; wind confidence decays with age.
- Settings behavior matches NETTO_SETTINGS_S100_PARITY.md.

3.2 Edge Cases
- Startup with no wind -> Tier C, netto invalid.
- Circling with noisy GPS -> TAS held, no flicker.
- Replay time regressions -> safe reset or hold.
- No baro or GNSS gaps -> confidence degrades, not fabricated outputs.

3.3 Test Coverage Required
- JVM unit tests for confidence and tiering.
- Wind staleness/persistence tests.
- TE gating tests.
- Golden replay determinism and stability tests.

-------------------------------------------------------------------------------

4) Required Checks (run and pass)

Must run:
- ./gradlew enforceRules
- ./gradlew testDebugUnitTest
- ./gradlew lintDebug
- ./gradlew assembleDebug

If applicable:
- ./gradlew detekt
- ./gradlew ktlintCheck
- ./gradlew connectedDebugAndroidTest

Report:
- Commands run and results.
- Fixes applied if any failures occur.

-------------------------------------------------------------------------------

5) Notes / ADR

Record decisions here as they arise:
- Decision:
- Alternatives:
- Why chosen:
- Impact / risks:
- Follow-ups:

-------------------------------------------------------------------------------

6) Agent Output Format (MANDATORY)

At the end of each phase:
- Phase N Summary:
  - What changed
  - Files touched
  - Tests run
  - Results
  - Next steps

At the end of the task:
- Done checklist
- PR-ready summary (what/why/how)
- Manual verification steps (2-5)

