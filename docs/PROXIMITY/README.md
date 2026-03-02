# PROXIMITY README

Purpose: single entry point for ADS-B smart proximity behavior, ownership, and tests.

## Current Behavior Contract

- Proximity is ownship-relative only (current glider/phone position).
- Trend-aware policy is authoritative in repository/store domain path.
- UI maps `proximity_tier` to colors only; UI does not re-evaluate business thresholds.
- ADS-B snapshot now includes EMERGENCY-audio FSM telemetry (state, cooldown, trigger counters).
- Smart rule:
  - closing traffic can escalate to alert colors,
  - non-closing traffic de-escalates to green after recovery dwell.

## Tier and Trend Rules

- Distance bands:
  - `> 5 km` -> green
  - `2..5 km` -> amber
  - `<= 2 km` -> red
- Closing hysteresis:
  - enter closing: `>= 1.0 m/s`
  - exit closing: `<= 0.3 m/s`
- Minimum trend sample delta: `800 ms`
- Recovery dwell before de-escalation to green: `4 s`
- First valid sample is alert-eligible until trend is established.

## Emergency Rules

Emergency is highest priority but only when all are true:

- distance `<= 1 km`
- inbound collision geometry match
- actively closing
- sample fresh (`ageSec <= 20`)

If stale or not closing, emergency is disabled.

## Ownship and Fallback Semantics

- With ownship reference:
  - full trend/tier/emergency policy applies.
- Without ownship reference:
  - tier becomes `NEUTRAL`
  - emergency disabled
  - distance/bearing can use center fallback for geometry only.

## SSOT Ownership

- Raw ADS-B target cache + trend memory + tier decision:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
- Trend evaluator state machine:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbProximityTrendEvaluator.kt`
- Repository wiring and publish cadence:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
- Map tier->color mapping:
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbProximityColorPolicy.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbGeoJsonMapper.kt`

## Test Map

- Evaluator hardening:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbProximityTrendEvaluatorTest.kt`
- Store hardening:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreTest.kt`
- Repository deterministic transitions:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`

## Required Verification

Run for non-trivial proximity changes:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Related Docs

- `docs/ADS-b/ADSB.md`
- `docs/PROXIMITY/CHANGE_PLAN_ADSB_SMART_PROXIMITY_TREND_2026-03-01.md`
- `docs/PROXIMITY/CHANGE_PLAN_ADSB_EMERGENCY_AUDIO_ALERTS_2026-03-02.md`
