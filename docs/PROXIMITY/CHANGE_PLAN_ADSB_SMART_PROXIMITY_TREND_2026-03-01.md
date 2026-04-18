# CHANGE_PLAN_ADSB_SMART_PROXIMITY_TREND_2026-03-01.md

## 0) Metadata

- Title: ADS-B smart proximity trend policy (de-escalate when no longer closing)
- Owner: Codex
- Date: 2026-03-01
- Issue/PR: TBD
- Status: Completed (Phases 0-4)

## 1) Scope

- Problem statement:
  - Current ADS-B proximity coloring is distance-tiered only (`<=2km red`, `2..5km amber`, otherwise green).
  - This can keep aircraft in alert colors even when they are no longer closing.
  - Requirement: once aircraft is not getting closer, return to normal color.
- Why now:
  - Improve usefulness and reduce nuisance urgency while keeping ownship-only semantics.
- In scope:
  - Add trend-aware proximity evaluation based on ownship-relative distance over monotonic time.
  - De-escalate from alert colors back to normal color when closing trend ends (with anti-flicker dwell).
  - Preserve emergency override, but require active closure for emergency.
  - Keep ownship-only ADS-B proximity; no OGN coupling.
- Out of scope:
  - ATC-style resolution advisories or maneuver guidance.
  - Any ADS-B-to-OGN conflict logic.
  - Provider/network cadence changes unrelated to proximity policy.
- User-visible impact:
  - ADS-B markers alert when traffic is approaching.
  - If traffic stops closing or diverges, marker color returns to normal (green) after short dwell.

### 1.1 Research Anchors and Guidance

Primary references used for policy direction:

1. FAA ATAS page (official FAA):
   - ATAS uses "proximity-prediction algorithms" and is designed to avoid "excessive nuisance alerts."
   - https://www.faa.gov/air_traffic/technology/adsb/pilot/atas
2. EUROCONTROL collision-avoidance validation platform:
   - Collision alerting should be tested across realistic encounter sets and stress scenarios.
   - https://www.eurocontrol.int/project/collision-avoidance-validation

Inference for XCPro:
- Distance-only tiers are insufficient for nuisance-resistant behavior.
- Trend + hysteresis + dwell should be first-class policy elements.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Raw ADS-B targets | `AdsbTrafficStore` via `AdsbTrafficRepository` | `StateFlow<List<AdsbTrafficUiModel>>` | VM/UI-owned traffic caches for policy |
| Ownship reference availability | `AdsbTrafficRepository` | `usesOwnshipReference` on target/snapshot | UI recomputation of ownship-reference truth |
| Per-target trend memory (prev distance/time, closure state, dwell timers) | `AdsbTrafficStore` internal state | Internal only | UI/overlay trend state mirrors |
| Derived proximity tier and closure flags | `AdsbTrafficStore` + new evaluator | `AdsbTrafficUiModel` fields | Color-layer distance-only reinterpretation |
| Color mapping | `AdsbProximityColorPolicy` | Map expression from tier | Overlay-side ad-hoc threshold logic |

### 2.2 Dependency Direction

Confirmed flow remains:

`UI -> ViewModel -> use-case -> repository`

- Modules/files touched:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/*`
  - `feature/map/src/main/java/com/trust3/xcpro/map/Adsb*`
  - `feature/map/src/test/java/com/trust3/xcpro/adsb/*`
  - `feature/map/src/test/java/com/trust3/xcpro/map/*`
  - `docs/ADS-b/ADSB.md`
  - `docs/ARCHITECTURE/PIPELINE.md` (if wiring semantics change)
- Boundary risk:
  - Reintroducing proximity policy in UI expression based on interpolated distance.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Alert decision (red/amber/emergency vs normal) | `AdsbProximityColorPolicy` distance expression | `AdsbTrafficStore` + `AdsbProximityTrendEvaluator` | Policy should use closure trend from SSOT, not render interpolation | Unit tests + mapper/policy tests |
| Color expression role | `AdsbProximityColorPolicy` thresholds + colors | `AdsbProximityColorPolicy` tier-to-color only | Keep UI mapping simple and deterministic | Color-policy tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `AdsbProximityColorPolicy.expression()` | Direct `distance_m` thresholding in map expression | Use `proximity_tier` property authored by store policy | Phase 2 |
| Overlay smoothing path | Interpolated distance can influence color if expression is distance-based | Tier computed upstream; smoothing remains visual-only | Phase 2 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Distance delta `dd/dt` for closure trend | Monotonic | Stable, deterministic trend math |
| De-escalation dwell timer | Monotonic | Anti-flicker and replay determinism |
| Stale gating for emergency eligibility | Monotonic | Prevent stale threat persistence |
| UI display labels/time text | Wall | Presentation only |

Explicitly forbidden comparisons:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Proximity trend evaluation stays in repository/store path (non-main).
- Primary cadence/gating sensor:
  - ADS-B poll/refresh and ownship updates.
- Hot-path latency budget:
  - Trend evaluation + tier assignment for displayed targets should remain within existing selection budget (target: <5ms for 30 targets on typical device).

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules:
  - Same input sequence and monotonic deltas must produce same tier transitions.
  - No wall-time influence on policy state.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Alert sticks red/amber after divergence | SSOT/domain policy correctness | Unit tests | `AdsbProximityTrendEvaluatorTest` |
| Flicker from GPS/ADS-B noise | Stability and anti-flicker intent | Unit tests | evaluator jitter/hysteresis tests |
| UI interpolation changes threat color | UI must not own business logic | Unit + review | `AdsbProximityColorPolicyTest`, overlay tests |
| Ownship-missing fallback regression | ADS-B ownship contract | Unit tests | `AdsbTrafficStoreTest`, `AdsbGeoJsonMapperTest` |
| OGN coupling reintroduced | Ownship-only contract | Unit tests + review | `MapScreenViewModelTest` ownship/OGN independence |

## 3) Data Flow (Before -> After)

Before:

`AdsbTrafficRepository -> AdsbTrafficStore(distance/emergency) -> AdsbTrafficUiModel(distance,bearing,isEmergency) -> AdsbGeoJsonMapper(distance,hasOwnship,isEmergency) -> AdsbProximityColorPolicy(distance thresholds) -> map color`

After:

`AdsbTrafficRepository -> AdsbTrafficStore(distance + trend state + proximity tier) -> AdsbTrafficUiModel(..., proximityTier, closingRate, isClosing) -> AdsbGeoJsonMapper(..., proximity_tier) -> AdsbProximityColorPolicy(tier mapping) -> map color`

Notes:
- `AdsbDisplayMotionSmoother` remains visual-only.
- Threat tier comes from store SSOT policy, not interpolated distance.

## 4) Implementation Phases

### Phase 0 - Baseline lock and constants agreement

- Goal:
  - Lock expected behavior and thresholds before changing policy logic.
- Files to change:
  - New plan doc (this file)
  - Optional: `docs/ADS-b/ADSB.md` section stub for "smart proximity mode"
- Tests to add/update:
  - Add failing/ignored test cases that encode the new requirement:
    - "closing -> alert color", "not closing -> return normal color".
- Exit criteria:
  - Baseline tests document current behavior and desired behavior deltas.

### Phase 1 - Trend evaluator in domain/store path

- Goal:
  - Implement deterministic closure-trend state machine in repository/store path.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficStore.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficModels.kt`
  - New: `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbProximityTrendEvaluator.kt`
- Tests to add/update:
  - New: `AdsbProximityTrendEvaluatorTest`
  - Update: `AdsbTrafficStoreTest`
- Initial policy constants (proposal):
  - `CLOSING_ENTER_MS = 1.0`
  - `CLOSING_EXIT_MS = 0.3`
  - `RECOVERY_DWELL_MS = 4_000`
  - `MIN_TREND_SAMPLE_DT_MS = 800`
  - `EMERGENCY_MAX_AGE_SEC = 20`
- Exit criteria:
  - Closing/non-closing classification is stable and deterministic.

### Phase 2 - Tier-based color policy and de-escalation

- Goal:
  - Move map color decision to tier mapping; enforce "not closing -> normal color."
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/AdsbProximityColorPolicy.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlay.kt` (if mapper property wiring changes)
- Tests to add/update:
  - `AdsbProximityColorPolicyTest`
  - `AdsbGeoJsonMapperTest`
- Tier rules (proposal):
  - `NEUTRAL`: no ownship reference.
  - `EMERGENCY`: emergency geometry + closing + fresh.
  - `RED`: `distance<=2000m` and closing.
  - `AMBER`: `2000m<distance<=5000m` and closing.
  - `GREEN` (normal): all non-closing cases (including diverging/steady).
- Exit criteria:
  - Color expression no longer interprets raw distance thresholds directly.
  - Diverging traffic returns to normal color after recovery dwell.

### Phase 3 - Details/debug semantics and operator clarity

- Goal:
  - Make reason for color state understandable.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbSelectedTargetDetails.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbMarkerDetailsSheet.kt`
  - Optional debug: `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapTrafficDebugPanels.kt`
- Tests to add/update:
  - Details sheet semantics tests.
  - `MapScreenViewModelTest` selected details correctness with ownship fallback.
- Exit criteria:
  - Details explicitly indicate trend/alert state and ownship-reference validity.

### Phase 4 - Hardening, replay validation, and tuning pass

- Goal:
  - Verify stability under jitter, stale transitions, replay, and edge cases.
- Files to change:
  - `feature/map/src/test/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/*` targeted tests
  - `docs/ADS-b/ADSB.md` final behavior contract
  - `docs/ARCHITECTURE/PIPELINE.md` if flow ownership wording changes
- Tests to add/update:
  - Sequence tests: closing->diverging->closing re-entry.
  - Jitter/noise tests near threshold.
  - Stale-target deactivation tests.
  - Replay determinism tests for tier transitions.
- Exit criteria:
  - Required checks pass, docs synchronized, no architecture drift.

## 5) Test Plan

- Unit tests:
  - `5001m, closing` -> green
  - `5000m, closing` -> amber
  - `2000m, closing` -> red
  - `1500m, diverging` -> green (after recovery dwell)
  - emergency geometry + closing + fresh -> emergency
  - emergency geometry + not-closing -> non-emergency tier
  - no ownship reference -> neutral
  - jitter around constant distance does not oscillate tiers
- Replay/regression tests:
  - same replay input produces identical tier transitions.
- UI/instrumentation tests (if needed):
  - marker color transitions for approach vs diverge sequence.
  - details-sheet trend/ownship-reference semantics.
- Degraded/failure-mode tests:
  - stale age disables emergency escalation.
  - missing track disables emergency and falls back to non-emergency tier.
- Boundary tests for removed bypasses:
  - expression uses `proximity_tier`, not `distance_m` thresholds as policy source.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Over-aggressive de-escalation hides close but diverging traffic too early | Medium | Add dwell + minimum sample quality + tuned thresholds; validate on replay/encounter cases | XCPro Team |
| Noise-induced color flicker | High | Enter/exit hysteresis + minimum dt + recovery dwell | XCPro Team |
| UI and SSOT disagree on threat state | High | Single tier authority in store; policy maps tier only | XCPro Team |
| Regression of ownship-missing neutral behavior | Medium | Keep explicit neutral branch + tests | XCPro Team |
| Future drift back to distance-only UI logic | Medium | Tests asserting expression source and evaluator ownership | XCPro Team |

## 7) Acceptance Gates

- No architecture/coding-rule violations.
- ADS-B proximity remains ownship-relative only.
- No ADS-B-to-OGN coupling introduced.
- Smart rule satisfied:
  - If target is not getting closer, alert color returns to normal (green) after recovery dwell.
- Emergency classification requires active closure + freshness and remains highest priority when valid.
- Replay remains deterministic.
- Docs updated for final semantics.

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 3 UI semantics.
  - Phase 2 tier-to-color mapping.
  - Phase 1 evaluator internals.
- Recovery steps if regression is detected:
  1. Revert to previous distance-only color policy (`2km/5km`).
  2. Keep ownship-only + neutral fallback contract intact.
  3. Re-run `enforceRules`, `testDebugUnitTest`, `assembleDebug`.
  4. Re-introduce smart policy behind a feature flag for controlled rollout.

## 9) Practical Advice (Initial Tuning)

- Start conservative:
  - Require at least two valid trend samples before alert escalation.
  - Use short but non-zero recovery dwell (3-5s) to prevent flip-flop.
- Keep policy explainable:
  - Alert if "close and closing", normalize if "not closing."
- Delay advanced CPA/TCPA math until Phase 4:
  - First ship deterministic trend behavior, then evaluate CPA-based refinement with replay datasets.

## 10) Completion Record (2026-03-01)

### 10.1 Phase Outcome

- Phase 0: Completed
- Phase 1: Completed
- Phase 2: Completed
- Phase 3: Completed
- Phase 4: Completed

### 10.2 Phase 4 Hardening Coverage Added

- `feature/map/src/test/java/com/trust3/xcpro/adsb/AdsbProximityTrendEvaluatorTest.kt`
  - closing -> diverging -> re-closing re-entry behavior
  - constant-distance jitter stability (no false closing oscillation)
  - same-sequence determinism across evaluator instances
- `feature/map/src/test/java/com/trust3/xcpro/adsb/AdsbTrafficStoreTest.kt`
  - re-entry tier re-escalation after de-escalation
  - stale age disables emergency escalation (`ageSec > 20`)
  - deterministic tier transition sequence for same input timeline
- `feature/map/src/test/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
  - repository-level deterministic proximity-tier transition scenario across repeated runs

### 10.3 Required Verification Evidence

Executed locally on 2026-03-01:

- `python scripts/arch_gate.py` -> PASS
- `./gradlew enforceRules` -> PASS
- `./gradlew testDebugUnitTest` -> PASS
- `./gradlew assembleDebug` -> PASS

Note:
- One transient Windows test-output file lock was encountered during an intermediate targeted run
  (`feature/map/build/test-results/testDebugUnitTest/binary/output.bin`); rerun passed without code changes.

### 10.4 Quality Rescore

- Architecture cleanliness: 4.8 / 5
  - Evidence: tier policy remains SSOT-owned in store/evaluator path; UI remains tier-mapping only.
- Maintainability / change safety: 4.7 / 5
  - Evidence: explicit trend-state and sequence hardening tests at evaluator/store/repository layers.
- Test confidence on risky paths: 4.7 / 5
  - Evidence: re-entry, jitter, stale emergency cutoff, and determinism scenarios covered.
- Overall ADS-B proximity slice quality: 4.8 / 5
  - Evidence: smart de-escalation behavior is documented and regression-locked.
- Release readiness (ADS-B proximity slice): 4.6 / 5
  - Remaining risk: threshold tuning may need replay-dataset calibration for nuisance-alert balance.
