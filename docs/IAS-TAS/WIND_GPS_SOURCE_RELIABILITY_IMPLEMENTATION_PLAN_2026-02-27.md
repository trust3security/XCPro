# Wind GPS Source Reliability Implementation Plan (Live Only)

Date: 2026-02-27
Owner: XCPro Team
Status: Proposed

## 1. Purpose

Improve live IAS/TAS/Wind reliability by reducing incorrect wind usage and source flapping between `WIND` and `GPS`, while preserving current architecture rules (MVVM + UDF + SSOT, deterministic domain logic, injected clocks).

This plan targets item `1` from the current IAS/TAS improvement set:
- Wind/GPS source reliability (high impact).

## 2. Scope

In scope:
- Live/flying path only.
- Source selection and gating behavior for wind-derived airspeed vs GPS fallback.
- TAS/IAS stability and `tasValid` consistency.
- TE gating consistency with selected source.
- Test coverage for reliability behavior.

Out of scope:
- Replay behavior changes.
- External airspeed hardware integration (not present in this release).
- UI redesign of cards/widgets.

## 3. Current State (Baseline)

Current behavior:
- Wind is used for TAS/IAS only when `windState.isAvailable == true` and `confidence >= 0.1`.
- Otherwise selection falls back to GPS ground speed (`IAS = TAS = GPS`).
- This can flap near threshold boundaries and can degrade IAS/TAS stability and labels.

Observed risk patterns:
- Confidence oscillation around boundary.
- Short wind dropouts causing frequent source switches.
- TE eligibility churn due to source churn.

## 4. Success Criteria

Target KPIs (live sessions with stable cruise/thermalling samples):
1. Source switch rate (`WIND <-> GPS`) reduced by >= 50 percent vs baseline.
2. TAS short-window jitter (3s stddev) reduced by >= 25 percent in stable segments.
3. TE on/off churn caused by source transitions reduced by >= 50 percent.
4. No false "sticky wind" beyond configured grace and stale limits.
5. All new gating behavior covered by deterministic unit tests.

## 5. Default Reliability Policy (Initial Values)

These are initial values for implementation and tuning:
- `WIND_ENTER_CONF_MIN = 0.15`
- `WIND_EXIT_CONF_MIN = 0.08`
- `WIND_MIN_GPS_SPEED_MS = 5.0`
- `WIND_SOURCE_MIN_DWELL_MS = 2500`
- `WIND_TRANSIENT_GRACE_MS = 1500`
- `WIND_RELIABILITY_DEBUG_LOG = true` in debug builds only

Rationale:
- Separate enter/exit thresholds add hysteresis.
- Dwell time prevents rapid oscillation.
- Grace window absorbs short transient drops without immediate fallback.
- Minimum GPS speed avoids low-speed wind-derived noise.

## 6. Phased Implementation

### Phase 0: Baseline and Metrics Instrumentation

Objective:
- Capture pre-change behavior and define test datasets.

Implementation tasks:
- Add counters/metrics hooks for:
  - source selected (`WIND`, `GPS`),
  - transition count (`WIND->GPS`, `GPS->WIND`),
  - reasons for rejection (`stale`, `low_conf`, `low_speed`, `no_vector`).
- Record baseline KPI snapshots from current code.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/` (new debug policy/metrics helper if needed)

Exit criteria:
- Baseline metrics captured and saved in PR notes.

### Phase 1: Policy Extraction

Objective:
- Remove inline wind eligibility decisions and centralize policy.

Implementation tasks:
- Introduce `WindAirspeedEligibilityPolicy` in domain layer.
- Evaluate:
  - wind availability,
  - confidence with hysteresis thresholds,
  - speed floor,
  - freshness constraints from existing state.
- Return both decision and reason code.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/WindAirspeedEligibilityPolicy.kt` (new)
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`

Exit criteria:
- No direct hardcoded `confidence >= 0.1` logic remains in call sites.
- Unit tests cover threshold boundaries.

### Phase 2: Source Stability Controller

Objective:
- Stabilize source transitions with dwell + grace behavior.

Implementation tasks:
- Add `AirspeedSourceStabilityController` (domain state helper).
- Apply:
  - enter/exit hysteresis,
  - minimum dwell time,
  - transient grace before forced fallback.
- Keep fallback to GPS deterministic when criteria fail beyond grace.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/AirspeedSourceStabilityController.kt` (new)
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FusionBlackboard.kt` (if shared state storage is preferred)

Exit criteria:
- Switch-rate reduction visible in deterministic sequence tests.
- No state leaks across reset paths.

### Phase 3: TAS/IAS and TE Consistency Hardening

Objective:
- Ensure selected source, `tasValid`, and TE eligibility remain consistent.

Implementation tasks:
- Drive final `chosenAirspeed` from stability controller output.
- Guarantee `tasValid` reflects stabilized, energy-eligible source only.
- Ensure TE gating reads stabilized source result, not raw per-frame pre-gate output.
- Preserve existing behavior where GPS source is not energy-height-eligible.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/SensorFrontEnd.kt`
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/AirspeedModels.kt` (only if metadata extension is needed)

Exit criteria:
- No mismatches between source label, `tasValid`, and TE eligibility in tests.

### Phase 4: Test Matrix Expansion

Objective:
- Lock reliability behavior with deterministic test coverage.

Implementation tasks:
- Add/extend tests for:
  - confidence oscillation around thresholds,
  - transient dropout shorter than grace window,
  - sustained low confidence triggering fallback,
  - low-speed suppression of wind-derived TAS/IAS,
  - dwell-time blocks immediate bounce-back switching,
  - TE gating consistency through transitions.

Primary files:
- `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/` (new policy/controller tests)

Exit criteria:
- New reliability suite passes and is deterministic.

### Phase 5: Live Tuning and Rollout

Objective:
- Tune thresholds with real flight logs and ship safely.

Implementation tasks:
- Compare post-change KPIs to baseline.
- Adjust constants in one config location only.
- Add feature flag for rollback if needed.
- Keep debug observability on in debug builds.

Primary files:
- `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FlightMetricsConstants.kt` (or policy config holder)
- optional feature flag location (existing config path)

Exit criteria:
- KPI targets reached or justified with documented tradeoffs.
- Rollback path verified.

### Phase 6: Documentation Sync

Objective:
- Keep design and implementation aligned.

Implementation tasks:
- Update IAS/TAS deep pass doc with final thresholds and behavior.
- Document reliability policy and reason codes.
- Keep explicit note: no external airspeed hardware in this release.

Primary files:
- `docs/IAS-TAS/IAS_TAS_DEEP_CODE_PASS_2026-02-27.md`
- optional mirror: `docs/LEVO/IAS_TAS_DEEP_CODE_PASS_2026-02-27.md`

Exit criteria:
- Docs match code and tests in the same change series.

## 7. Test Plan (Required)

Unit tests:
1. Policy threshold entry/exit cases.
2. Hysteresis around confidence boundary.
3. Dwell-time enforcement.
4. Grace-window behavior.
5. Low-speed rejection behavior.
6. TE gating consistency with stabilized source.

Integration sanity checks:
1. Stable thermalling: fewer source flips.
2. Stable cruise: smooth TAS/IAS and correct labels.
3. Wind dropout scenario: deterministic fallback within configured limits.

## 8. Risks and Mitigations

Risk: Over-stabilization may delay valid source recovery.
- Mitigation: keep dwell and grace bounded; tune with KPI review.

Risk: Under-stabilization still allows flapping.
- Mitigation: separate enter/exit thresholds and transition reason telemetry.

Risk: Hidden coupling with TE/netto behavior.
- Mitigation: explicit transition tests that assert TE and `tasValid` coherence.

## 9. Rollout Checklist

- [ ] Phase 0 baseline captured.
- [ ] Policy extraction merged with tests.
- [ ] Stability controller merged with tests.
- [ ] TAS/IAS/TE coherence tests green.
- [ ] KPI delta measured (before vs after).
- [ ] Docs updated in same PR series.
- [ ] `./gradlew enforceRules` passes.
- [ ] `./gradlew testDebugUnitTest` passes.
- [ ] `./gradlew assembleDebug` passes.

## 10. Expected Outcome

If implemented as above, this plan should produce a high-confidence improvement to live wind-derived TAS/IAS reliability:
- fewer incorrect wind selections,
- fewer WIND/GPS source oscillations,
- smoother IAS/TAS output,
- more stable TE behavior under borderline wind confidence.

## 11. Phase 2 Missed Items (Deep Pass Addendum, 2026-02-28)

These items were identified in a follow-up live-only code pass and should be incorporated before or during Phase 2.

1. Serialize stateful fusion decisions across the two live loops.
- Current engine runs vario and GPS loops in separate coroutines on `Dispatchers.Default`, and both paths can call `emit()` -> `CalculateFlightMetricsUseCase.execute(...)`.
- `CalculateFlightMetricsUseCase` and related helpers are stateful (`prevTeSpeed`, circling detector, blackboard windows, smoothers), so Phase 2 controller state would also be vulnerable to race/interleave drift without serialization.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt:49`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:152`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:167`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:67`

2. Define controller interaction with existing 10s airspeed hold to avoid split semantics.
- `SensorFrontEnd` applies `resolveAirspeedHold(...)` only when incoming estimate is null.
- TE gating is computed earlier from `chosenAirspeed` (pre-hold), so a null/hold strategy can produce mismatches between TE eligibility and displayed `tasValid`/source.
- Phase 2 must explicitly choose one model:
  - a) controller always outputs explicit source (WIND or GPS), or
  - b) controller may output null, but TE/netto/display semantics are updated consistently.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:124`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:126`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/SensorFrontEnd.kt:58`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FusionBlackboard.kt:59`

3. Add a dedicated wind age gate for airspeed reliability (separate from wind UI stale horizon).
- Wind repository stale horizon is `1 hour`, which is too loose for TAS/IAS source reliability decisions.
- Phase 2 should introduce tighter `WIND_AIRSPEED_MAX_AGE_MS` in domain policy.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:377`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/model/WindState.kt:14`

4. Extend `WindState` with monotonic update clock to avoid cross-timebase age errors in policy.
- `WindState` exposes wall-time `lastUpdatedMillis`; policy-level age gating for auto/manual paths is safer with explicit monotonic update timestamp.
- Add `lastUpdatedClockMillis` in producer path and consume it in policy.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/model/WindState.kt:9`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:321`

5. Align downstream wind confidence usage with stabilized source decision.
- STF uses raw `windConfidence` blending (`0.35..0.70`) even when wind eligibility may be rejected.
- Phase 2 should pass an effective/stabilized wind confidence (or 0.0 when source is not wind-eligible) to avoid control mismatch.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:317`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/SpeedToFlyCalculator.kt:69`

6. Phase 0 observability should include transition counters, not just per-frame decision buckets.
- Current counters capture decision reason frequency only.
- Phase 2 tuning needs explicit transition metrics (`WIND->GPS`, `GPS->WIND`) and dwell/grace hit counts.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:69`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:398`

7. Revisit speed floors used for source reliability.
- Current fallback floor remains `0.5 m/s`, while other code treats true movement at higher thresholds (`2.0 m/s` in `GPSData.isMoving`).
- Phase 2 should lock one consistent threshold strategy for wind eligibility and fallback stability tests.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:467`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorData.kt:38`

## 12. Phase 2 Missed Items (Deep Pass Addendum #2, 2026-02-28)

8. Flight-state detector still reads raw airspeed source, not stabilized selected airspeed.
- `FlightStateRepository` consumes `AirspeedDataSource` directly and forwards `trueAirspeedMs`/`airspeedReal` to `FlyingStateDetector`.
- If Phase 2 stabilizes only metrics path, flying-state transitions can still flap independently and feed inconsistent `isFlying`.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightStateRepository.kt:69`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightStateRepository.kt:106`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FlyingStateDetector.kt:41`

9. Shared live samples in engine are unsynchronized/non-volatile.
- `latestWindState` and `latestAirspeedSample` are written in collector coroutines and read in both emit loops with no visibility guard.
- Phase 2 source-stability state should avoid relying on these fields unless execution is serialized or memory visibility is hardened.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:81`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:83`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:140`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:194`

10. Negative time deltas can accidentally keep hold active.
- Airspeed hold checks `now - lastAirspeedTimestamp <= SPEED_HOLD_MS` without explicit non-negative guard.
- If loop interleave/time regression occurs, negative deltas pass the condition and may over-hold.
- Phase 2 controller/dwell logic must explicitly reject negative age.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FusionBlackboard.kt:67`

11. Phase 0 counters are not thread-safe under concurrent execute paths.
- Decision counters use unsynchronized `mutableMapOf` mutation inside use-case.
- With dual-loop concurrent execute, counters can race and produce unreliable metrics.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:69`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:399`

12. Reliability thresholds can drift because policy and fallback use separate constants.
- Policy min GPS speed uses `WIND_AIRSPEED_MIN_GPS_SPEED_MS`; fallback source uses private `MIN_FALLBACK_GPS_SPEED_MS`.
- Phase 2 should centralize these thresholds or tie them by contract.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/WindAirspeedEligibilityPolicy.kt:21`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:467`

13. Wind-derived TAS uses GPS bearing/speed without using available accuracy fields.
- GPS model already carries `bearingAccuracyDeg` and `speedAccuracyMs`, but policy/estimator do not gate on them.
- In low-quality fixes, source-stability may still accept noisy wind-derived TAS.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorData.kt:20`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:110`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/WindEstimator.kt:17`

14. Manual/external wind age uses wall-time and can be extended by wall-clock rollback.
- Wind overrides are timestamped with `clock.nowWallMs()`.
- Age check for non-auto wind uses `(gpsWallMillis - lastUpdatedMillis).coerceAtLeast(0L)`, so backward wall jumps can suppress staleness progression.
- Phase 2 should prefer monotonic-age tracking for reliability gating.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindOverrideRepository.kt:70`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindOverrideRepository.kt:98`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:266`

## 13. Phase 2 Missed Items (Deep Pass Addendum #3, 10-Pass Recurring Audit, 2026-02-28)

15. `isFlying` input to metrics path is also read from unsynchronized shared state.
- Engine writes `latestFlightState` in one collector coroutine and reads it in both emit loops.
- This can produce frame-level mismatch between source-stability decisions and `isFlying`-gated downstream behavior.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:82`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:141`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:196`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:308`

16. Decision observability has no production export path yet.
- `windAirspeedDecisionCounts()` is only consumed in tests; no runtime telemetry/debug surface currently consumes it.
- Phase 0 KPI capture needs a production-visible sink (debug logs/diagnostics flow/dev panel).
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:395`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt:406`

17. Speed/hold thresholds are duplicated across domain helpers and can drift.
- `FlightMetricsConstants` defines moving/hold thresholds, while `FlightCalculationHelpers` also keeps independent copies for netto recency.
- Phase 2 should centralize these or define explicit contract separation to prevent silent divergence.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FlightMetricsConstants.kt:22`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightCalculationHelpers.kt:58`

18. Wind selection precedence can over-prioritize external/manual signals for too long.
- `WindSelectionUseCase` chooses `external` over `manual` whenever auto is not newer-than-manual, independent of relative recency between external/manual.
- Combined with broad stale horizon, this can keep non-optimal override source active for reliability decisions.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/domain/WindSelectionUseCase.kt:24`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/domain/WindSelectionUseCase.kt:25`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:377`

19. Boundary-condition tests around threshold equality are missing.
- Policy tests cover below/above paths but not exact boundary values (`== minConfidence`, `== minGpsSpeedMs`) where code uses `<` and `<=` in different places.
- Phase 2 should lock equality behavior explicitly to avoid off-by-one regression.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/WindAirspeedEligibilityPolicy.kt:47`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/WindAirspeedEligibilityPolicy.kt:52`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/WindAirspeedEligibilityPolicyTest.kt:67`

20. Display-side IAS/TAS stability can still look noisy after source stabilization.
- `cardFlightDataFlow` buckets several fields but does not bucket IAS/TAS values; card labels still pivot on `tasValid`.
- Even with Phase 2 source hysteresis, unbucketed speed values can continue to appear jittery in cards.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/map/FlightDataManager.kt:207`
  - `feature/map/src/main/java/com/trust3/xcpro/map/FlightDataManagerSupport.kt:21`
  - `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:136`

## 14. Phase 2 Missed Items (Deep Pass Addendum #4, Recurring Audit Continuation, 2026-02-28)

21. Engine stop/reset leaves stale wind/flight-state caches alive.
- `stop()` clears `latestAirspeedSample` but does not clear `latestWindState` or `latestFlightState`.
- On restart, early frames can consume stale state until new flows arrive.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:81`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:82`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:191`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt:224`

22. `FlightDataEmissionState` is shared mutable state across both emit loops without synchronization.
- Baro and GPS loops both read/write `lastUpdateTime`/`varioValidUntil`; emitter also mutates the same state.
- Phase 2 timing/dwell metrics should not rely on these fields unless execution is serialized.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:141`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:174`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:293`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataEmitter.kt:152`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataEmitter.kt:177`

23. Decision counters currently measure policy evaluations, not selected-source outcomes.
- `recordWindDecision(...)` is executed before final source selection; external sample can still override wind.
- KPI interpretation can be misleading if counters are treated as "wind used" rather than "wind eligible."
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:100`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:104`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:124`

24. `levoNettoHasWind` user-facing semantics can diverge from selected airspeed source.
- `hasWindForLevo` is derived from wind eligibility, not from actual selected source (`chosenAirspeed`).
- This value is propagated to UI/card fields and can indicate wind availability while IAS/TAS source is not wind.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:124`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:274`
  - `feature/map/src/main/java/com/trust3/xcpro/flightdata/FlightDisplayMapper.kt:83`
  - `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:262`

25. Phase 0 KPI counters should be event-based, not frame-based.
- Current decision counting is performed on every `execute()` invocation from high-cadence emit paths.
- For Phase 2 tuning, transition/dwell KPIs should count state transitions and durations, not per-frame evaluations.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:100`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:174`

## 15. Saturation Check (Recurring Passes Completed, 2026-02-28)

Additional recurring live-only Phase 2 code passes were run after Addendum #4.

Result:
- No new non-duplicate Phase 2 misses were found.
- The current addendum set (Sections 11-14) is treated as complete for this audit cycle.

## 16. Phase 2 Missed Items (Deep Pass Addendum #5, Recurring Audit Continuation, 2026-02-28)

26. Wind confidence is dropped before UI/trail wind consumers.
- `WindState` carries `confidence`, and TAS/IAS eligibility uses it, but UI/trail paths consume only `quality/stale/speed`.
- `RealTimeFlightData` has no wind-confidence field, and `applyWindState(...)` does not forward confidence.
- Result: wind can remain visually "valid" while TAS/IAS has already fallen back to GPS on low confidence.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/model/WindState.kt:11`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/WindAirspeedEligibilityPolicy.kt:45`
  - `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt:63`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenObservers.kt:192`
  - `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:415`

27. Wind-valid thresholds diverge across live surfaces (0.2 m/s vs 0.5 m/s).
- Variometer wind-arrow validity uses `0.2 m/s` (`FlightDataManager`).
- Heading wind-use, cards, and trail wind-use all gate at `0.5 m/s`.
- This can produce contradictory "wind valid" behavior between arrow/label and card/heading/trail outputs.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/map/FlightDataManager.kt:42`
  - `feature/map/src/main/java/com/trust3/xcpro/MapScreenUtils.kt:72`
  - `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:415`
  - `feature/map/src/main/java/com/trust3/xcpro/map/trail/domain/TrailProcessor.kt:166`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/OverlayPanels.kt:195`

28. Heading resolver uses wind without confidence gating.
- `convertToRealTimeFlightData(...)` enables wind influence for heading when `isAvailable && speed > 0.5`, without checking confidence.
- TAS/IAS eligibility already rejects low-confidence wind in domain policy, so heading correction can diverge from stabilized source behavior.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/MapScreenUtils.kt:72`
  - `feature/map/src/main/java/com/trust3/xcpro/MapScreenUtils.kt:79`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/WindAirspeedEligibilityPolicy.kt:45`

29. Low-confidence wind integration coverage is still missing in UI/trail tests.
- `ConvertToRealTimeFlightDataTest` checks stale-wind heading fallback but does not cover low-confidence wind behavior.
- `TrailProcessorTest` exercises `windState = null` paths only and does not assert confidence-threshold handling.
- References:
  - `feature/map/src/test/java/com/trust3/xcpro/ConvertToRealTimeFlightDataTest.kt:112`
  - `feature/map/src/test/java/com/trust3/xcpro/map/trail/TrailProcessorTest.kt:22`

## 17. Saturation Check (Recurring Passes Completed After Addendum #5, 2026-02-28)

Additional recurring live-only Phase 2 code passes were run after Addendum #5.

Result:
- No further non-duplicate Phase 2 misses were found.
- The current addendum set (Sections 11-16) is treated as complete for this audit cycle.

## 18. Phase 2 Missed Items (Deep Pass Addendum #6, Recurring Audit Continuation, 2026-02-28)

30. Phase 2 source-stability controller behavior is still not implemented in the live path.
- Source selection is still frame-local and immediate (`EXTERNAL ?: WIND ?: GPS`) with no dwell/grace/hysteresis state machine.
- Policy still uses single thresholds only (`WIND_AIRSPEED_CONF_MIN`, `WIND_AIRSPEED_MIN_GPS_SPEED_MS`) rather than enter/exit hysteresis controls.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:96`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:124`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FlightMetricsConstants.kt:26`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/WindAirspeedEligibilityPolicy.kt:19`

31. Manual/external wind candidates are selected without freshness gating.
- Manual/external candidates are passed directly to selection and, when selected, published with `stale = false` on every sample.
- Non-auto stale age checks run only in the fallback branch where no candidate is selected, so old override timestamps can stay active indefinitely while override values remain present.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:225`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:239`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:257`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/domain/WindSelectionUseCase.kt:23`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindOverrideRepository.kt:44`

32. Invalid GPS wind-input samples bypass degradation/staleness updates.
- `processSample(...)` returns early when `trackRad` or `groundSpeedMs` is non-finite.
- This bypasses stale progression logic and can keep the previous wind state alive until later valid GPS samples or full no-data reset.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:171`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:257`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:141`

33. Missing live tests for override-age expiry and invalid-GPS degradation paths.
- Current selection tests cover precedence but not age-based rejection of stale manual/external overrides.
- Current fusion tests cover confidence decay and circling/replay hold behavior, but not non-finite GPS-input stale/degrade behavior.
- References:
  - `feature/map/src/test/java/com/trust3/xcpro/weather/wind/WindSelectionUseCaseTest.kt:15`
  - `feature/map/src/test/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepositoryTest.kt:27`

## 19. Saturation Check (Recurring Passes Completed After Addendum #6, 2026-02-28)

Additional recurring live-only Phase 2 code passes were run after Addendum #6.

Result:
- No further non-duplicate Phase 2 misses were found.
- The current addendum set (Sections 11-18) is treated as complete for this audit cycle.

## 20. Phase 2 Missed Items (Deep Pass Addendum #7, Recurring Audit Continuation, 2026-02-28)

34. Wind quality scale contract is inconsistent between override publication and confidence normalization.
- `WindOverride` default quality is `6`, while confidence normalization clamps to a max measurement quality of `5`.
- Result: `WindState.quality` can be `6` while `confidence` is normalized on a 0..5 basis, which creates mixed semantics for policy/diagnostics/UI interpretation.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/model/WindOverride.kt:10`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:339`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:364`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:376`

35. Live manual/external wind and live external-airspeed sources are currently dormant in production wiring.
- Repository setters exist for manual wind, external wind, and external airspeed updates, but call-site audit shows no production invocations outside repository definitions.
- Result: Phase 2 tuning of manual/external/external-sensor precedence has limited live impact until writer paths are actually wired.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindOverrideRepository.kt:67`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindOverrideRepository.kt:95`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindOverrideRepository.kt:111`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/ExternalAirspeedRepository.kt:16`
  - `feature/map/src/main/java/com/trust3/xcpro/di/WindSensorModule.kt:57`

36. Override-source updates are blocked until a strictly newer GPS clock sample arrives.
- The combined sensor/override flow emits when manual/external overrides change, but `processSample(...)` exits early when `gpsClockMillis <= lastGpsClockMillis`.
- Result: same-GPS-timestamp override updates are deferred/ignored until a newer GPS clock appears; stale/degrade progression for those emissions is also bypassed.
- References:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:107`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:118`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:176`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt:225`

37. Missing tests for quality-scale contract and same-timestamp override-update behavior.
- Current wind selection/fusion tests do not lock the override quality-scale behavior (`6` publication vs `5` confidence normalization).
- Current tests do not assert expected behavior when override changes arrive without a newer GPS clock sample.
- References:
  - `feature/map/src/test/java/com/trust3/xcpro/weather/wind/WindSelectionUseCaseTest.kt:15`
  - `feature/map/src/test/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepositoryTest.kt:27`
  - `feature/map/src/test/java/com/trust3/xcpro/weather/wind/WindSensorFusionRepositoryTest.kt:44`

## 21. Saturation Check (Recurring Passes Completed After Addendum #7, 2026-02-28)

Additional recurring live-only Phase 2 code passes were run after Addendum #7 (10-pass sweep plus confirmation sweep).

Result:
- No further non-duplicate Phase 2 misses were found.
- The current addendum set (Sections 11-20) is treated as complete for this audit cycle.
