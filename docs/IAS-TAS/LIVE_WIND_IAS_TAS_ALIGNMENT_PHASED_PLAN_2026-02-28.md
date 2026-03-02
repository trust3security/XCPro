# Live Wind/IAS/TAS Alignment Phased Implementation Plan (Phone-Only)

Date: 2026-02-28
Owner: XCPro Team
Status: Proposed
Scope: Live flight path only

## 1. Purpose

Close the remaining reliability gaps after Phase 2 source-stability implementation so IAS/TAS values, source labels, wind visuals, and downstream wind consumers remain consistent and measurable during live flight.

## 2. Hard Constraints

1. Live-only implementation. Replay behavior must remain unchanged.
2. Phone-only assumption. No external device dependency is required for this plan.
3. MVVM + UDF + SSOT boundaries must remain intact.
4. Domain/fusion timing must stay deterministic and use injected clocks/timebases.

## 3. Current Baseline (After Phase 2)

Implemented:
1. Wind/GPS source hysteresis + dwell + grace in domain.
2. Stabilized source is used for TAS/IAS and TE gating.
3. Unit tests cover hysteresis/grace/dwell in domain path.

Remaining gaps:
1. UI/heading/trail wind-consumer gating can still diverge from stabilized TAS/IAS source logic.
2. Source reliability telemetry is still decision-frame heavy and not transition-event centered.
3. Live engine shared-state reads/writes across loops still need hardening.
4. Flight-state detector can diverge from stabilized source behavior.

## 4. Success Criteria

1. No contradictory wind-valid behavior between IAS/TAS source label and wind UI/heading/trail behavior in equivalent confidence/speed conditions.
2. Transition telemetry reports event counters for `GPS->WIND`, `WIND->GPS`, grace-hold uses, and dwell blocks.
3. Shared-state live loop hardening eliminates stale-cache and visibility race regressions in tests.
4. Flight-state transitions show reduced mismatch with stabilized source switching.
5. Required verification commands pass:
   1. `./gradlew enforceRules`
   2. `./gradlew testDebugUnitTest`
   3. `./gradlew assembleDebug`

## 5. Phased Plan

## Phase 0: Baseline and Telemetry Contract

Objective:
Define exactly what to measure before further behavior changes.

Implementation:
1. Define reliability event model in domain:
   1. `GPS_TO_WIND`
   2. `WIND_TO_GPS`
   3. `WIND_GRACE_HOLD`
   4. `WIND_DWELL_BLOCK`
2. Add clear metric semantics doc comment for each counter.
3. Keep counters debug/diagnostics-visible only.

Likely files:
1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt`
2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
3. `feature/map/src/main/java/com/example/xcpro/sensors/VarioDiagnosticsSample.kt` (if exposing through diagnostics)

Tests:
1. Counter increment tests for each transition event.
2. Ensure no counter mutation on no-op frames.

Exit criteria:
1. Event counters exist and are test-verified.
2. Counter semantics are documented in code.

## Phase 1: Propagate Wind Confidence to UI Model

Objective:
Make UI and downstream consumers able to apply the same confidence contract as domain source logic.

Implementation:
1. Add wind confidence to the UI-facing flight model used by cards/overlays.
2. Forward confidence from `WindState` in map observer conversion path.
3. Keep field optional/default-safe for backwards compatibility.

Likely files:
1. `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt`
2. `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt`
3. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`

Tests:
1. Extend conversion tests for confidence propagation.
2. Validate low-confidence wind yields expected downstream flags.

Exit criteria:
1. Wind confidence is available wherever wind validity decisions are made in UI/trail paths.

## Phase 2: Unify Wind Validity Policy Across Live Surfaces

Objective:
Remove inconsistent thresholds and gating differences between domain and UI/trail/heading consumers.

Implementation:
1. Introduce shared live wind-validity helper for non-domain consumers.
2. Align thresholds for:
   1. confidence floor
   2. speed floor
   3. stale/availability handling
3. Replace ad-hoc checks in heading resolver, trail processor, and wind-arrow validity logic.

Likely files:
1. `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
2. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`
3. `feature/map/src/main/java/com/example/xcpro/map/trail/domain/TrailProcessor.kt`
4. `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt`

Tests:
1. Add low-confidence and boundary-threshold tests in:
   1. `feature/map/src/test/java/com/example/xcpro/ConvertToRealTimeFlightDataTest.kt`
   2. `feature/map/src/test/java/com/example/xcpro/map/trail/TrailProcessorTest.kt`
2. Add threshold equality tests for non-domain consumers.

Exit criteria:
1. Wind validity behavior is consistent for TAS/IAS label logic, heading logic, and trail/card outputs.

## Phase 3: Event-Based Reliability Telemetry

Objective:
Support live tuning with meaningful event/duration metrics instead of per-frame counts.

Implementation:
1. Extend controller to emit transition events and hold/dwell hits.
2. Aggregate into deterministic counters in use-case scope.
3. Expose to diagnostics/debug sink.

Likely files:
1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt`
2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
3. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/` (if surfaced in diagnostics UI)

Tests:
1. Transition sequence tests validating exact event counts.
2. Reset behavior tests confirming counters reset with `reset()`.

Exit criteria:
1. Debug tooling can report real transition behavior for live tuning.

## Phase 4: Live Engine Shared-State Hardening

Objective:
Reduce loop interleave and stale-read risk for wind/flight-state/airspeed shared samples.

Implementation:
1. Harden shared sample visibility between collectors and emit loops.
2. Ensure stop/reset clears all relevant cached live state.
3. Keep emission ordering deterministic and avoid introducing replay coupling.

Likely files:
1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`

Tests:
1. Add regression tests for cache clear on stop.
2. Add concurrency/ordering tests where feasible for engine sample handoff semantics.

Exit criteria:
1. No stale wind/flight-state cache use immediately after stop/start in tests.

## Phase 5: Flight-State Alignment with Stabilized Source

Objective:
Reduce mismatches where flight-state detection reacts differently from stabilized IAS/TAS source behavior.

Implementation:
1. Audit and align `FlightStateRepository` airspeed confidence/source semantics with Phase 2 stabilized source expectations.
2. Preserve fail-safe behavior when no wind solution is reliable.

Likely files:
1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt`
2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/FlyingStateDetector.kt`

Tests:
1. Add tests for source-transition scenarios affecting `isFlying`.
2. Validate reduced false toggles in low-confidence wind intervals.

Exit criteria:
1. Flight-state and TAS/IAS source switching are behaviorally consistent in transition tests.

## Phase 6: Live Tuning and Rollout

Objective:
Tune thresholds and release safely for phone-only live flight use.

Implementation:
1. Run controlled live sessions and capture telemetry from Phase 3 counters.
2. Tune thresholds only in centralized constants/policy config.
3. Keep rollback capability via single-policy revert commit.

Tuning targets:
1. Lower source-flip rate without over-sticky wind behavior.
2. Maintain quick recovery to wind when confidence/speed genuinely recover.
3. Keep UI wind behavior aligned with TAS/IAS source result.

Exit criteria:
1. KPI deltas are documented and accepted.
2. Rollout patch is small and reversible.

## 6. Verification Matrix

For each phase:
1. Run targeted unit tests for touched area.
2. Run full required checks before phase completion:
   1. `./gradlew enforceRules`
   2. `./gradlew testDebugUnitTest`
   3. `./gradlew assembleDebug`

Optional when device/emulator available:
1. `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`

## 7. Risk Register

1. Over-stabilization risk:
   1. Wind source may recover too slowly.
   2. Mitigation: bounded dwell/grace and event telemetry monitoring.
2. Under-stabilization risk:
   1. Source flapping persists.
   2. Mitigation: tighten enter/exit gap and speed-quality gating.
3. Cross-surface drift risk:
   1. UI/trail logic drifts from domain logic again.
   2. Mitigation: shared helper + boundary tests.
4. Concurrency regressions:
   1. Hardening changes can alter loop timing.
   2. Mitigation: targeted engine regression tests and phase-isolated merges.

## 8. Definition of Done

1. All phases completed or explicitly deferred with rationale.
2. No replay behavior changes introduced.
3. No external-device dependency introduced.
4. Architecture constraints remain enforced.
5. Required verification commands pass on final patch set.

## 9. Implementation Audit (Code Pass, 2026-02-28)

Audit goal:
Verify which parts of this phased plan are implemented and identify misses with concrete references.

### 9.1 Implemented Baseline (Confirmed)

1. Domain source stabilization exists (hysteresis + dwell + grace):
   1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt`
2. Metrics path uses stabilized source for TAS/IAS/TE:
   1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:96`
   2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:115`
3. Use-case execution is synchronized:
   1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:69`

### 9.2 Missed Items by Phase

## Phase 0 Misses (Telemetry Contract)

1. Transition-event model (`GPS_TO_WIND`, `WIND_TO_GPS`, `WIND_GRACE_HOLD`, `WIND_DWELL_BLOCK`) is not implemented.
   1. No symbols found in codebase for these events.
2. Current counters are still policy-evaluation buckets, not transition events.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:67`
   2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:101`
3. Counters are not exposed through diagnostics/debug runtime models.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/VarioDiagnosticsSample.kt:5`

## Phase 1 Misses (Wind Confidence Propagation)

1. UI-facing model still has no explicit wind-confidence field.
   1. `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt:63`
   2. `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt:78`
2. Map observer wind copy path publishes speed/direction/quality but not confidence.
   1. `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt:193`
   2. `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt:195`

## Phase 2 Misses (Unified Live Wind Validity Policy Across Surfaces)

1. Heading resolver still uses ad-hoc wind-valid rules with no confidence gate.
   1. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt:72`
2. Trail processor still uses quality/stale/speed checks with no confidence threshold.
   1. `feature/map/src/main/java/com/example/xcpro/map/trail/domain/TrailProcessor.kt:163`
3. Wind indicator validity path still uses quality + speed only (no confidence).
   1. `feature/map/src/main/java/com/example/xcpro/map/FlightDataManagerSupport.kt:55`
4. Card wind formatting still uses quality + speed only (no confidence).
   1. `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:415`
   2. `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:445`
5. Thresholds remain inconsistent across live surfaces.
   1. `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt:42` (`0.2 m/s`)
   2. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt:72` (`>0.5 m/s`)
   3. `feature/map/src/main/java/com/example/xcpro/map/trail/domain/TrailProcessor.kt:183` (`0.5 m/s`)

## Phase 3 Misses (Event-Based Reliability Telemetry)

1. No controller-generated transition events are emitted.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:15`
2. No event counters for grace-hold and dwell-block are aggregated.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:394`
3. No diagnostics sink currently includes source-transition telemetry.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/VarioDiagnosticsSample.kt:5`

## Phase 4 Misses (Live Engine Shared-State Hardening)

1. Shared live samples are mutable non-volatile vars written/read across coroutines.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:81`
   2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:82`
   3. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:83`
   4. Writer collectors:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:140`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:141`
      3. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:142`
   5. Reader points:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:194`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:306`
2. `stop()` still clears only `latestAirspeedSample`, not `latestWindState`/`latestFlightState`.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:224`
3. Shared `FlightDataEmissionState` remains mutable state across both loops and emitter.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:141`
   2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:174`
   3. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:293`
   4. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt:152`
4. No dedicated engine tests found for stop/reset cache clear on these shared fields.

## Phase 5 Misses (Flight-State Alignment)

1. Flight-state detector still consumes raw airspeed source flow directly.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:30`
   2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:69`
2. `airspeedReal` is derived from raw sample validity, not stabilized TAS/IAS source decision.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:100`
   2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:107`

## Phase 6 Misses (Live Tuning/Rollout)

1. Event telemetry needed for tuning is not yet present (blocked by Phase 3 misses).
2. No KPI dataset path exists yet for transition-based tuning within current implementation.

### 9.3 Test Coverage Gaps (Plan-Specific)

1. Heading conversion tests cover stale-wind behavior but not low-confidence wind behavior.
   1. `feature/map/src/test/java/com/example/xcpro/ConvertToRealTimeFlightDataTest.kt:112`
2. Trail tests currently use `windState = null` scenarios and do not cover confidence-threshold gating.
   1. `feature/map/src/test/java/com/example/xcpro/map/trail/TrailProcessorTest.kt:22`
   2. `feature/map/src/test/java/com/example/xcpro/map/trail/TrailProcessorTest.kt:150`

### 9.4 Priority Order for Next Implementation Pass

1. Phase 1 + Phase 2 together:
   1. Add wind confidence to `RealTimeFlightData`.
   2. Unify wind-validity helper and replace ad-hoc UI/trail/heading/card checks.
2. Phase 3:
   1. Add transition-event telemetry and diagnostics surface.
3. Phase 4:
   1. Harden engine shared-state visibility and stop/reset cache clears.
4. Phase 5:
   1. Align flight-state input path with stabilized source semantics.

## 10. Implementation Audit Addendum (Intensive Live-Only Pass, 2026-02-28)

Scope note:
This addendum is live-path only (`replay` intentionally excluded).

### 10.1 New Misses Found Beyond Section 9

## Additional Phase 2 Misses (Unified Live Wind Validity Policy)

1. Orientation pipeline still uses wind without confidence-aligned gating.
   1. Live path feeds `RealTimeFlightData` directly into orientation on every sample:
      1. `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt:119`
   2. `OrientationDataSource` accepts wind when speed is only `> 0` and does not check confidence:
      1. `feature/map/src/main/java/com/example/xcpro/OrientationDataSource.kt:245`
      2. `feature/map/src/main/java/com/example/xcpro/OrientationDataSource.kt:246`
      3. `feature/map/src/main/java/com/example/xcpro/OrientationDataSource.kt:247`
   3. `HeadingResolver` adds another independent wind-speed threshold (`> 0.1`) with no confidence:
      1. `feature/map/src/main/java/com/example/xcpro/orientation/HeadingResolver.kt:43`

2. Wind publication into UI model still relies on `isAvailable` (quality/stale) and omits confidence.
   1. `WindState.isAvailable` does not include confidence:
      1. `feature/map/src/main/java/com/example/xcpro/weather/wind/model/WindState.kt:14`
   2. `applyWindState(...)` copies wind fields whenever `isAvailable` is true:
      1. `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt:179`
      2. `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt:193`

3. Trail drift render path has a second independent wind-valid threshold outside trail ingestion policy.
   1. `SnailTrailMath` applies `point.windSpeedMs > 0.5` separately:
      1. `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailMath.kt:49`
      2. `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailMath.kt:50`
   2. This is separate from `TrailProcessor.resolveWindSample(...)` thresholding:
      1. `feature/map/src/main/java/com/example/xcpro/map/trail/domain/TrailProcessor.kt:161`
      2. `feature/map/src/main/java/com/example/xcpro/map/trail/domain/TrailProcessor.kt:166`

4. Threshold centralization is still violated across live consumers.
   1. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt:72` (`> 0.5`)
   2. `feature/map/src/main/java/com/example/xcpro/orientation/HeadingResolver.kt:43` (`> 0.1`)
   3. `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt:42` (`0.2`)
   4. `feature/map/src/main/java/com/example/xcpro/map/trail/domain/TrailProcessor.kt:183` (`0.5`)
   5. `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailMath.kt:50` (`0.5`)
   6. `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:415` (`> 0.5`)

## Additional Phase 4 Misses (Live Engine Shared-State Hardening)

1. Cross-loop cached sensor fields remain broadly shared mutable state beyond the previously listed wind fields.
   1. Shared cache declarations:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:117`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:129`
   2. GPS loop writes cache values:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:258`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:265`
   3. Vario loop reads these cached values:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:115`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:123`
      3. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:173`

2. Cached vario/baro results are also exchanged across loops without explicit synchronization boundaries.
   1. Vario loop writes:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:169`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:171`
   2. GPS loop reads:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:279`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:306`

### 10.2 Additional Test Coverage Gaps

1. No test covers low-confidence wind behavior through the real orientation live path.
   1. Existing map-orientation tests use a fake source where `updateFromFlightData(...)` is a no-op:
      1. `feature/map/src/test/java/com/example/xcpro/MapOrientationManagerTest.kt:152`
      2. `feature/map/src/test/java/com/example/xcpro/MapOrientationManagerTest.kt:161`

2. No direct unit tests found for wind-indicator validity helper behavior under confidence/threshold alignment cases.
   1. Helper path:
      1. `feature/map/src/main/java/com/example/xcpro/map/FlightDataManagerSupport.kt:41`
      2. `feature/map/src/main/java/com/example/xcpro/map/FlightDataManagerSupport.kt:55`

3. Wind card tests assert quality/speed behavior but do not cover confidence-aligned gating scenarios.
   1. Current wind card tests:
      1. `dfcards-library/src/test/java/com/example/dfcards/CardDataFormatterTest.kt:167`
      2. `dfcards-library/src/test/java/com/example/dfcards/CardDataFormatterTest.kt:180`

### 10.3 Updated Next-Fix Priority (After Addendum)

1. Extend Phase 1 + 2 implementation to include orientation and trail-render consumers, not only card/indicator/trail-ingest paths.
2. Introduce one shared live wind-validity contract/helper and remove all independent thresholds in UI/trail/orientation/card code.
3. Add focused tests for orientation live path, wind-indicator helper, and low-confidence card/heading behavior.
4. Continue Phase 4 hardening beyond `latestWindState`/`latestFlightState`/`latestAirspeedSample` to cover cross-loop cache exchange.

## 11. Comprehensive Live-Only Code Pass Addendum (2026-03-01)

Scope note:
1. Live/flying path only.
2. Replay path intentionally excluded.
3. External-device paths intentionally excluded (phone-only production scope).

### 11.1 Remaining Misses Found

1. Transition telemetry is still frame-based for hold/block events, not episode-based.
   1. `AirspeedSourceStabilityController` records `WIND_GRACE_HOLD` on every grace-held evaluation and `WIND_DWELL_BLOCK` on every dwell-blocked evaluation:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:84`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:109`
   2. Counters aggregate each event occurrence directly:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:408`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:412`
   3. Impact: dwell/grace KPIs remain sample-rate sensitive and can be misread during tuning.

2. Wind source-telemetry is emitted but still not visible in diagnostics UI.
   1. Counters are published in diagnostics samples:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/VarioDiagnosticsSample.kt:10`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/VarioDiagnosticsSample.kt:11`
      3. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:220`
      4. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:222`
   2. `VarioDiagnosticsScreen` currently renders only vario/filter metrics and does not surface these maps:
      1. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt:75`
      2. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt:137`
   3. Impact: transition/decision telemetry is available to logs/state but not reviewable in-app.

3. Flight-state source classification is still permissive for unknown source labels.
   1. Live path treats airspeed as "real" when `tasValid=true` and label is not GPS-derived:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:118`
   2. GPS-derived classification currently accepts only `"GPS"` or blank:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:164`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:165`
   3. Impact: unexpected/future labels (for example `"UNKNOWN"`) can be treated as real airspeed in flying-state gating.

4. Edge-case divergence remains possible between held WIND airspeed source and live wind-valid flag.
   1. During WIND-active dropout, controller can hold the last WIND estimate within grace:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:83`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:85`
   2. Live wind-valid policy still requires current `windState.vector` and `windState.isAvailable`:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/LiveWindValidityPolicy.kt:17`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/LiveWindValidityPolicy.kt:18`
   3. Consumers rely on that policy for UI/heading/trail wind visibility:
      1. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt:73`
      2. `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt:181`
      3. `feature/map/src/main/java/com/example/xcpro/map/trail/domain/TrailProcessor.kt:187`
   4. Impact: in rare dropouts, IAS/TAS source can remain `WIND` while visible wind state is suppressed.

### 11.2 Additional Coverage Gaps

1. No test currently validates the grace-hold edge where `airspeedSource == WIND` but live wind visibility is suppressed due unavailable `WindState`.
2. No dedicated test currently validates unknown-label handling in `FlightStateRepository.isGpsDerivedAirspeed(...)`.

## 12. Implementation Update (2026-03-01, Live Phone-Only)

Implemented in this pass:

1. Transition hold/block telemetry now records once per contiguous episode instead of every frame.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:19`
   2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:85`
   3. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:118`

2. Grace-hold now requires a current wind candidate; missing current wind vector falls back immediately to GPS (prevents WIND-source hold without live wind state).
   1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:82`
   2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:95`

3. Flight-state source classification hardened: only trusted non-GPS labels (`WIND`, `SENSOR`) are treated as real airspeed.
   1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:118`
   2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:164`

4. Targeted regression tests added/updated for new behavior.
   1. `feature/map/src/test/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityControllerTest.kt:20`
   2. `feature/map/src/test/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityControllerTest.kt:193`
   3. `feature/map/src/test/java/com/example/xcpro/sensors/FlightStateRepositoryAglFreshnessTest.kt:113`

Validation run:
1. `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.sensors.domain.AirspeedSourceStabilityControllerTest" --tests "com.example.xcpro.sensors.FlightStateRepositoryAglFreshnessTest" --tests "com.example.xcpro.sensors.domain.CalculateFlightMetricsUseCaseTest"`
2. Result: PASS.

## 13. Comprehensive Live-Only Code Pass (Post-Implementation, 2026-03-01)

Scope note:
1. Live/flying path only.
2. Replay intentionally excluded.
3. External-device sources intentionally excluded (phone-only production scope).

### 13.1 Status of Previously Reported Misses

Closed in code:
1. Frame-based grace/dwell event spam:
   1. Events are now emitted once per contiguous episode.
   2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:90`
   3. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:122`
2. Unknown airspeed source labels treated as real in flying-state path:
   1. Source trust-list is now explicit (`WIND`, `SENSOR`).
   2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:118`
   3. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:164`
3. Grace-hold/source-visibility divergence when current wind candidate is missing:
   1. Missing current wind candidate now forces immediate fallback to GPS.
   2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:89`

Still open:
1. Transition/decision counters are not yet surfaced in diagnostics UI.
   1. Counters are emitted in diagnostics samples:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:220`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:222`
   2. Diagnostics screen still does not render those fields:
      1. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt:75`

### 13.2 Additional Misses Found In This Pass

1. No use-case-level regression asserts episode-based transition counting semantics end-to-end.
   1. Controller unit tests assert episode behavior:
      1. `feature/map/src/test/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityControllerTest.kt:195`
   2. Use-case transition-counter test still only checks `>= 1` and does not lock episode cardinality:
      1. `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt:677`
   3. Impact: future wiring changes can regress episode semantics without failing use-case tests.

2. Diagnostics telemetry remains cumulative-only (lifetime counters), with no built-in interval-delta view.
   1. Counters are accumulated in mutable maps and exposed as totals:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:67`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:397`
   2. Impact: tuning interpretation can still require manual differencing when reviewing live sessions.

### 13.3 Updated Priority (After This Comprehensive Pass)

1. Surface wind source transition/decision counters in `VarioDiagnosticsScreen` (or dedicated debug panel).
2. Add one use-case-level test that locks per-episode transition count behavior through `CalculateFlightMetricsUseCase`.
3. Optionally add interval-delta telemetry view (or helper) for easier tuning interpretation.

## 14. Comprehensive Live-Only Code Pass (Recurring Audit, 2026-03-01)

Scope note:
1. Live/flying path only.
2. Replay intentionally excluded.
3. External-device paths intentionally excluded (phone-only production scope).

### 14.1 New Miss Found In This Pass

1. Grace-hold "last wind estimate" cache is updated before eligibility filtering, allowing rejected wind candidates to overwrite held state.
   1. Controller updates `lastWindEstimate` whenever `windCandidate` exists, before checking `windDecision.eligible`:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:39`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:40`
   2. During WIND-active grace-hold, returned estimate is `lastWindEstimate`:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:90`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt:95`
   3. Because of (1), ineligible current wind (for example low confidence) can replace the prior accepted estimate and still drive the grace-held output.
   4. Impact: source stability can appear preserved while value stability degrades under rejected-wind intervals.

### 14.2 Additional Coverage Gap

1. Current controller tests assert source/events across grace-hold but do not assert that held numeric airspeed remains pinned to last accepted estimate when current candidate is rejected.
   1. `feature/map/src/test/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityControllerTest.kt:20`
   2. `feature/map/src/test/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityControllerTest.kt:195`

### 14.3 Saturation Status

1. No other new non-duplicate live-only misses were found in this recurring pass beyond Section 14.1.

## 15. Requested Additions (1-4) - Next Implementation Block (2026-03-01)

Scope note:
1. Live/flying path only.
2. Replay intentionally excluded.
3. External-device paths intentionally excluded (phone-only production scope).

### 15.1 Item 1: Fix Grace-Hold Cache Contamination In Source Controller

Problem:
1. `lastWindEstimate` can be overwritten by an ineligible wind candidate before eligibility filtering (Section 14.1).

Implementation:
1. Update `AirspeedSourceStabilityController` so `lastWindEstimate` is mutated only for accepted wind candidates:
   1. Cache update must occur after eligibility decision.
   2. Ineligible candidates must not replace the held estimate.
2. Preserve existing live behavior where missing current wind candidate disables grace-hold and falls back to GPS immediately.
3. Preserve episode-based transition event semantics implemented in Section 12.

Likely file:
1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityController.kt`

Tests:
1. Run controller unit tests plus Item 2 regression test.

Exit criteria:
1. Grace-held numeric estimate remains pinned to the last accepted wind estimate when current wind is present but ineligible.
2. No regression in source transition behavior/events.

### 15.2 Item 2: Add Controller Numeric Hold Regression Test

Objective:
1. Lock the grace-hold numeric behavior to prevent value drift while source stays `WIND`.

Implementation:
1. Add a deterministic controller sequence test:
   1. Accept eligible wind candidate A and transition to `WIND`.
   2. Provide ineligible wind candidate B during grace window.
   3. Assert source remains `WIND` due to grace.
   4. Assert returned estimate remains candidate A (not candidate B).
2. Keep assertions exact for IAS/TAS numeric values (not only source/events).

Likely file:
1. `feature/map/src/test/java/com/example/xcpro/sensors/domain/AirspeedSourceStabilityControllerTest.kt`

Exit criteria:
1. Test fails against pre-fix behavior and passes with Item 1 fix.

### 15.3 Item 3: Add Use-Case Exact Episode-Count Regression Test

Objective:
1. Enforce end-to-end episode-count semantics in `CalculateFlightMetricsUseCase`, not only in controller-local tests.

Implementation:
1. Replace/extend `>= 1` transition-counter assertions with exact expected cardinality in deterministic transition sequences.
2. Include scenarios where multiple frames occur within a single grace-hold or dwell-block episode and assert count increments once per episode.
3. Keep coverage for `GPS_TO_WIND`, `WIND_TO_GPS`, `WIND_GRACE_HOLD`, and `WIND_DWELL_BLOCK`.

Likely file:
1. `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt`

Exit criteria:
1. Use-case-level tests fail if episode semantics regress to frame-based counting.

### 15.4 Item 4: Surface Transition/Decision Counters In Diagnostics UI

Objective:
1. Make reliability counters directly visible in-app for live tuning and field verification.

Implementation:
1. Render `sourceTransitionCounts` and `sourceDecisionCounts` in `VarioDiagnosticsScreen`.
2. Keep display robust for empty/null maps (explicit "none" or equivalent placeholder).
3. Optional enhancement in same pass if low risk: show interval delta since previous sample/session reset to reduce manual differencing burden.

Likely files:
1. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt`
2. `feature/map/src/main/java/com/example/xcpro/sensors/VarioDiagnosticsSample.kt` (only if display contract needs adjustment)

Tests:
1. Add/update diagnostics rendering tests for counter visibility where test harness exists.
2. At minimum, add deterministic formatter/unit test coverage for counter text generation.

Exit criteria:
1. Diagnostics view exposes both counter maps in live mode.
2. Visibility change does not modify replay pipeline behavior.

### 15.5 Execution Order For This Block

1. Item 1 (controller fix).
2. Item 2 (controller numeric regression test).
3. Item 3 (use-case episode-cardinality regression test).
4. Item 4 (diagnostics surface and optional delta view).

## 16. Comprehensive Live-Only Code Pass Addendum (2026-03-01)

Scope note:
1. Live/flying path only.
2. Replay intentionally excluded.
3. External-device paths intentionally excluded (phone-only production scope).

### 16.1 Additional Misses Found Beyond Section 15

1. Counter snapshot access is not synchronized while counters are mutated in synchronized execute path.
   1. Mutable counters are stateful use-case fields:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:67`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:68`
   2. Counters are mutated inside `@Synchronized execute(...)`:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:70`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:403`
      3. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:408`
   3. Snapshot getters are unsynchronized and can run concurrently with execute on parallel emit paths:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:397`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:400`
      3. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:220`
      4. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:223`
   4. Impact:
      1. Risk of inconsistent diagnostics snapshots under loop interleave.
      2. Potential runtime map iteration race if counter access overlaps mutation.

2. Wind-direction finite-value hardening is incomplete before heading/wind UI consumption.
   1. Live wind usability policy checks speed but not direction finiteness:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/LiveWindValidityPolicy.kt:19`
   2. Heading resolver accepts any non-null `windFromDeg` and does not require finite direction:
      1. `feature/map/src/main/java/com/example/xcpro/orientation/HeadingResolver.kt:43`
      2. `feature/map/src/main/java/com/example/xcpro/orientation/HeadingResolver.kt:45`
   3. Wind vectors are published without explicit finite-component guard:
      1. `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt:336`
      2. `feature/map/src/main/java/com/example/xcpro/weather/wind/model/WindVector.kt:23`
   4. Impact:
      1. Non-finite wind direction can propagate to heading and wind UI paths.
      2. Can produce invalid heading outputs while source appears valid.

3. Source-label normalization remains inconsistent across live consumers.
   1. Live wind usability policy uses exact-case source match:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/LiveWindValidityPolicy.kt:6`
   2. Flying-state trust-list uses case-insensitive matching:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:165`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:166`
   3. Impact:
      1. Future source-label case drift can create UI/flight-state divergence.
      2. Reduces fail-safe consistency of source semantics.

4. Wind mapping contract is still split across two steps (converter + observer patch-up).
   1. Converter computes heading with wind-eligibility but emits default wind fields:
      1. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt:73`
      2. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt:83`
      3. `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt:143`
   2. Wind fields are then completed in `MapScreenObservers.applyWindState(...)`:
      1. `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt:92`
      2. `feature/map/src/main/java/com/example/xcpro/map/MapScreenObservers.kt:179`
   3. Impact:
      1. Correctness depends on an implicit call-order contract.
      2. Future callsites of converter alone can produce internally inconsistent UI models.

### 16.2 Additional Coverage Gaps

1. No concurrency-focused regression test currently validates counter snapshot safety under concurrent execute + diagnostics reads.
2. No explicit test validates rejection/degrade behavior for non-finite wind direction inputs across `LiveWindValidityPolicy` and `HeadingResolver`.
3. No test ensures source-label case normalization parity between live wind usability and flying-state trust-list.
4. No integration test locks the converter + observer wind-mapping contract as a single invariant.

### 16.3 Updated Priority After This Pass

1. Complete Section 15 Items 1-4 first (still highest impact and already scoped).
2. Add synchronization hardening for wind counter snapshot access (`CalculateFlightMetricsUseCase` getters/reset semantics).
3. Add finite-value guards for wind vectors/directions before heading and wind-valid usage.
4. Centralize/normalize airspeed source label matching so live consumers share one canonical interpretation.
5. Optionally collapse converter + wind patch-up into one explicit mapping contract (or add strict integration test if keeping split).

## 17. Comprehensive Live-Only Code Pass Addendum II (2026-03-01)

Scope note:
1. Live/flying path only.
2. Replay intentionally excluded.
3. External-device paths intentionally excluded (phone-only production scope).

### 17.1 Additional Misses Found Beyond Section 16

1. `stop()` can race with synchronized `execute(...)` because reset path is unsynchronized while sensor collectors remain active.
   1. Use-case compute path is synchronized:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:70`
   2. Reset path is not synchronized:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:379`
   3. Engine `stop()` invokes use-case reset:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:191`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:202`
   4. Long-lived collector jobs are launched in `init` and are not canceled in `stop()`:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:139`
      2. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:152`
      3. `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:167`
   5. Impact:
      1. Partial reset/interleave risk across source controller, counters, and smoothing state.
      2. Increases chance of transient TAS/IAS source flips or diagnostics discontinuities during stop/reset transitions.

2. Wind fusion invalid-input short-circuit can bypass stale/decay progression for existing wind state.
   1. Non-finite `trackRad` or `groundSpeedMs` causes immediate return:
      1. `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt:179`
   2. Stale expiration is only evaluated later in `processSample(...)`, after this early return:
      1. `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt:286`
   3. `handleNoData()` reset path is only used when GPS is `null`, not when GPS exists but values are non-finite:
      1. `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt:125`
      2. `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt:148`
   4. Impact:
      1. Existing wind can remain published/available longer than intended during invalid-GPS episodes.
      2. IAS/TAS wind eligibility and wind UI consumers may continue using frozen wind state until valid samples resume.

3. Diagnostics UI still renders literal placeholders for key live metrics because string interpolation is escaped.
   1. Health chips display literal `${...}` text instead of current values:
      1. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt:145`
      2. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt:146`
      3. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt:147`
   2. Chart min/now/max labels also display literal placeholders:
      1. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt:199`
      2. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt:201`
      3. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt:205`
   3. Impact:
      1. Live reliability tuning visibility is degraded because operators cannot read actual on-screen diagnostics values.
      2. Reduces confidence when validating Phase 2/3 behavior changes in field sessions.

### 17.2 Additional Coverage Gaps

1. No concurrency-focused regression test validates `stop()` interleave against `execute(...)`/`reset()` state integrity in the live engine path.
2. No wind-fusion regression test validates behavior when GPS exists but `trackRad` or `groundSpeedMs` is non-finite (including stale downgrade expectations).
3. No diagnostics rendering test verifies dynamic numeric interpolation for health chips and chart labels.

### 17.3 Updated Priority After This Pass

1. Complete Section 15 Items 1-4 first (still highest impact and already scoped).
2. Harden use-case/engine reset concurrency (`stop()` + `execute(...)` + counter snapshot access) as a single thread-safety block.
3. Fix wind-fusion invalid-GPS short-circuit so stale/decay handling still progresses under non-finite input episodes.
4. Fix diagnostics interpolation defects and add rendering/unit coverage so live telemetry is trustworthy.
5. Continue with Section 16 follow-ups (finite-direction hardening, source-label normalization, and converter/observer contract cleanup).

### 17.4 Implementation Status (2026-03-01)

Implemented in code:
1. Use-case thread-safety hardening:
   1. `reset()` and counter snapshot getters are now synchronized in:
      1. `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
2. Wind fusion non-finite GPS handling:
   1. Non-finite GPS samples now run stale/decay refresh logic instead of immediate no-op return in:
      1. `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt`
3. Diagnostics interpolation fix:
   1. Health chips and chart labels now render live numeric values (no escaped placeholders) in:
      1. `feature/map/src/main/java/com/example/xcpro/screens/diagnostics/VarioDiagnosticsScreen.kt`
4. Regression coverage added:
   1. Non-finite GPS stale progression test:
      1. `feature/map/src/test/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepositoryTest.kt`
