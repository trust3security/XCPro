# CHANGE_PLAN_ADSB_TURN_AWARE_EMERGENCY_HARDENING_2026-03-06.md

## 0) Metadata

- Title: ADS-B turn-aware emergency hardening (post-pass stability + confidence)
- Owner: XCPro Team
- Date: 2026-03-06
- Status: Draft
- Scope type: Focused hardening after turn-aware emergency rollout

## 0A) Implementation Update (2026-03-06)

Completed in code:

- Repository contract hardening:
  - `AdsbTrafficRepository.updateOwnshipMotion(...)` is no longer default no-op.
- Motion-quality ingestion guard in coordinator:
  - low-speed heading suppression,
  - poor speed-accuracy suppression.
- Trend cadence improvement:
  - store trend sample timestamp now supports ownship-reference sample time to refresh trend while ownship moves between provider packets.
- Emergency low-speed guard:
  - explicit low-motion context disables geometry emergency escalation.
- Added/updated unit tests for:
  - low-speed emergency suppression,
  - ownship-motion propagation in map traffic wiring,
  - trend refresh with unchanged target timestamp via ownship reference sample time.

Remaining from this plan:

- dedicated emergency hysteresis FSM in tier policy,
- explicit emergency/audio ineligibility reason contract surfaced in marker details and snapshot telemetry,
- target-track-missing fallback policy (derive or explicit surfaced non-emergency reason),
- deterministic replay corpus expansion for these edge cases.

## 1) Focused Code-Pass Findings (What Was Missed)

1. `EMERGENCY` can flicker because geometry is sampled frame-by-frame with no emergency hysteresis.
   - Evidence: `AdsbTrafficStore` promotes/demotes directly from `isEmergencyCollisionRisk` each select cycle.
   - File: `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficStore.kt`

2. Projected CPA/TCPA is bypassed when motion context is missing, falling back to heading-only emergency.
   - Risk: false emergency during low-speed/noisy-heading segments.
   - Evidence: fallback branch returns `true` after heading gate.
   - File: `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbCollisionRiskEvaluator.kt`

3. Ownship motion quality fields (`bearingAccuracyDeg`, `speedAccuracyMs`) are not used in emergency gating.
   - Risk: unstable emergency when GPS heading/speed confidence is degraded.
   - Files:
     - `feature/map/src/main/java/com/trust3/xcpro/map/model/MapUiModels.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`

4. `AdsbTrafficRepository.updateOwnshipMotion(...)` is a default no-op in the interface.
   - Risk: fake/test implementations can silently ignore required wiring.
   - File: `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepository.kt`

5. Debug/marker details do not expose why emergency audio is not eligible (no projected-conflict reason path).
   - Risk: pilot/developer cannot distinguish "safe pass" vs "missing motion confidence."
   - Files:
     - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficModels.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbMarkerDetailsSheet.kt`

6. Trend decisions are tied to target sample timestamp only; ownship-only movement updates do not create fresh trend samples.
   - Risk: emergency/post-pass decisions can lag until next provider update.
   - Evidence: trend evaluator receives `sampleMonoMs = target.receivedMonoMs` on each select path.
   - File: `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficStore.kt`

7. Stationary-heading behavior does not use a true pointing source; ADS-B motion uses GPS bearing only.
   - Risk: when stationary/slow, ownship heading used for motion projection is unreliable or absent.
   - Files:
     - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelMappers.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorData.kt`

8. Emergency geometry hard-requires target `trackDeg`; targets with missing track are forced non-emergency even when clearly converging by position trend.
   - Risk: false negatives (missed high-risk alerts) on incomplete provider vectors.
   - File: `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbCollisionRiskEvaluator.kt`

9. Emergency audio candidate can only reason from current boolean eligibility, without an explicit eligibility-reason contract in snapshot/UI.
   - Risk: poor explainability during pilot debugging ("Not eligible" ambiguity remains).
   - Files:
     - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficStore.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntimeSnapshot.kt`
     - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbMarkerDetailsSheet.kt`

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Ownship motion quality gate state | `AdsbTrafficRepositoryRuntime` | `AdsbTrafficSnapshot` debug fields + store inputs | UI-local emergency policy copies |
| Emergency stability state (enter/hold/exit) | `AdsbTrafficStore` trend/emergency state | `AdsbTrafficUiModel` tier/reason | Overlay-local emergency FSM |
| Emergency eligibility reason | ADS-B domain/store | `AdsbProximityReason` + marker details text | ad-hoc UI interpretation |

### 2.2 Dependency Direction

- Preserved: `UI -> use case -> repository/runtime -> store/domain evaluator`.
- No emergency policy in UI.
- No Android imports in domain evaluator/store.

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Trend sample dt and recovery dwell | Monotonic | deterministic closing/post-pass logic |
| Ownship reference freshness | Monotonic | stable runtime gating |
| Provider contact age | Wall (already isolated) | provider timestamp compatibility |

Forbidden comparisons remain unchanged: monotonic vs wall, replay vs wall.

## 3) Phased Implementation

## Phase 0 - Baseline Repro and Failing Tests

- Goal:
  - Lock current regressions with tests before changing behavior.
- Files:
  - `feature/map/src/test/java/com/trust3/xcpro/adsb/AdsbTrafficStoreTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/adsb/AdsbCollisionRiskEvaluatorTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTrafficSelectionTest.kt`
- Tests to add:
  - emergency flicker around heading/cpa threshold sequence.
  - low-speed/noisy-heading fallback sequence.
  - ownship-moved / target-not-updated sequence (trend freshness lag).
  - target-track-missing but position-trend-converging sequence.
  - coordinator->repository ownship motion propagation assertions.
- Exit:
  - new tests fail on current behavior where expected.
- Fast gate (phase end):
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.adsb.*" --tests "com.trust3.xcpro.map.MapScreenViewModelTrafficSelectionTest"`

## Phase 1 - Motion Quality Gating

- Goal:
  - Introduce explicit motion-confidence gating for projected emergency logic.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbCollisionRiskEvaluator.kt`
- Changes:
  - pass normalized motion quality signals (heading/speed confidence).
  - use explicit heading-source policy for ownship motion:
    - moving: GPS track,
    - stationary/slow: compass or "heading unavailable" (never synthetic 0 deg).
  - require confidence for projected conflict path.
  - on low confidence, degrade to non-emergency (not hard emergency fallback).
- Exit:
  - no emergency on low-confidence/no-motion context unless circling rule explicitly applies.
- Fast gate:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.adsb.AdsbCollisionRiskEvaluatorTest"`

## Phase 2 - Trend Cadence Correction + Emergency Hysteresis FSM

- Goal:
  - Remove `EMERGENCY <-> non-EMERGENCY` flicker near thresholds.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficStore.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbProximityTrendEvaluator.kt`
- Changes:
  - decouple trend freshness from target-only sample cadence where safe:
    - either synthesize trend updates on meaningful ownship delta, or
    - explicitly freeze escalation and surface reason when trend is stale.
  - add target-track fallback policy:
    - when provider track is missing, optionally derive short-window course from target position deltas,
    - or explicitly route to non-emergency with surfaced reason (no silent hard false).
  - add emergency enter/hold/exit counters or dwell windows.
  - asymmetric thresholds (enter stricter, exit slightly relaxed) for stability.
  - keep post-pass downgrade path consistent (`EMERGENCY -> RED/AMBER -> GREEN`).
- Exit:
  - deterministic de-escalation without red/green ping-pong.
- Fast gate:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.adsb.AdsbTrafficStoreTest" --tests "com.trust3.xcpro.adsb.AdsbProximityTrendEvaluatorTest"`

## Phase 3 - Reason Surfacing and Audio Eligibility Transparency

- Goal:
  - Surface why emergency/audio is blocked in UI/debug telemetry.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficModels.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficStore.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbMarkerDetailsSheet.kt`
- Changes:
  - extend reason model for key blocked states:
    - projected_conflict_not_likely
    - motion_confidence_low
    - heading_gate_failed
    - target_track_unavailable
    - trend_stale_waiting_for_fresh_sample
  - show explicit marker-details text instead of generic "Not eligible."
- Exit:
  - pilot/dev can explain each emergency/audio decision from UI.
- Fast gate:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.adsb.*MarkerDetails*"`

## Phase 4 - Interface/Test Safety Hardening

- Goal:
  - Remove silent no-op interface risk and harden fakes/tests.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepository.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTestSupport.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTestRuntime.kt`
- Changes:
  - make `updateOwnshipMotion(...)` abstract (no default body).
  - update all fakes/mocks to implement and expose last motion values.
  - add wiring assertions in coordinator/viewmodel tests.
- Exit:
  - compile-time enforcement for future implementations.
- Fast gate:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.*Traffic*"`

## Phase 5 - Deterministic Replay + Release Gates

- Goal:
  - prove turn-aware emergency stability and anti-nuisance behavior in deterministic replay.
- Files:
  - `feature/map/src/test/java/com/trust3/xcpro/adsb/AdsbEmergencyAudioReplayDeterminismTest.kt`
  - new focused replay test(s) under `feature/map/src/test/java/com/trust3/xcpro/adsb/`
  - `docs/ARCHITECTURE/PIPELINE.md` (if wiring semantics changed)
- Changes:
  - add replay traces for:
    - head-on then pass-through
    - thermal turn with heading noise
    - low-speed stationary ownship with degraded confidence
  - assert deterministic tier/audio transitions.
- Exit:
  - identical outputs across repeated runs for same replay data.
- Fast gate:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.adsb.*Replay*"`

## 4) Verification Strategy

- Per-phase fast gate: run only targeted tests/compile for touched scope.
- Final full gate (after all phases only):

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

- Optional when emulator/device is ready:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 5) File Budget Contract

- Keep every touched Kotlin file under `500` lines.
- Prefer extraction/splitting when a phase would push a file over cap.
- Do not merge a phase with line-budget drift.

## 6) Rollback Criteria

Rollback immediately if any of these occur during staged rollout:

1. Emergency tier flaps more than one transition per target per two fresh samples in nominal traffic.
2. Audio trigger rate spikes above baseline without matching replay-confirmed conflicts.
3. Deterministic replay mismatch on phase test corpus.
4. New architecture/rule gate failures (`arch_gate`, `enforceRules`, unit tests, assemble).
