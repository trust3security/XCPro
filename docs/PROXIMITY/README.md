# PROXIMITY README

Purpose: single entry point for ADS-B smart proximity behavior, ownership, and tests.

## Current Behavior Contract

- Proximity is ownship-relative only (current glider/phone position).
- Trend-aware policy is authoritative in repository/store domain path.
- UI maps `proximity_tier` to colors only; UI does not re-evaluate business thresholds.
- Proximity reason is SSOT-authored in domain/store (`proximityReason`) and surfaced in details/debug:
  - `no_ownship_reference`
  - `circling_rule_applied`
  - `geometry_emergency_applied`
  - `approach_closing`
  - `recovery_dwell`
  - `diverging_or_steady`
- ADS-B snapshot now includes EMERGENCY-audio FSM telemetry (state, cooldown, trigger counters).
- ADS-B snapshot includes Phase-5 KPI telemetry and reason-segmented counters:
  - `emergencyAudioKpis` (per-hour rates, disable-within-5min rate, retrigger/mismatch guards)
  - `proximityReasonCounts` (no-ref/circling/geometry/closing/dwell/steady)
- ADS-B rollout path now includes Phase-6 runtime gates:
  - master configured gate
  - shadow mode
  - cohort percent + deterministic bucket eligibility
  - rollback latch (auto kill-switch on KPI breach)
- Emergency marker flashing is runtime-configurable in ADS-B settings (`enabled` by default).
- Smart rule:
  - closing traffic can escalate to alert colors,
  - non-closing traffic de-escalates only after a real closing episode and only on fresh trend samples:
    - close red post-pass transitions use two fresh samples (`red -> amber -> green`),
    - amber post-pass transitions use one fresh sample (`amber -> green`),
    - stale/no-fresh samples never de-escalate,
    - stale/no-fresh samples hold the last resolved non-emergency tier (no `green -> amber` rebound flicker); stale evaluations can still escalate directly to red for safety.
  - closest-approach pass detection is also used for smart amber de-escalation:
    - when distance grows by at least `120 m` from the closest tracked sample (fresh trend sample, not in recovery dwell), amber can de-escalate to green even if closing-enter threshold was never crossed.
  - emergency geometry uses heading gate plus projected closest-approach (CPA/TCPA) when ownship and target motion vectors are available (thermal turns included through ownship motion updates).
  - target track fallback supports missing provider track values by deriving short-window course from target position deltas when movement is sufficient.
  - emergency escalation requires motion-confidence context for projected conflict checks; low-confidence/missing motion context does not escalate to EMERGENCY.
  - emergency anti-flicker hysteresis holds EMERGENCY across one fresh non-emergency sample and clears on sustained evidence.
  - ownship motion ingestion is confidence-aware:
    - low ground speed keeps speed but suppresses heading track for projection,
    - poor speed accuracy suppresses motion context for emergency projection.
  - trend freshness can use ownship-reference sample time (not only target packet time) so post-pass state can update while ownship moves between provider polls.
  - distance-tier anti-flicker hysteresis is applied so boundary jitter does not cause rapid amber/green or red/amber flashing.
  - large relative vertical separation is non-threat-clamped:
    - when `|relative altitude| >= 1200 m` (about 3900 ft), tier is forced to green and emergency audio is suppressed.
  - marker details expose explicit EMERGENCY/audio ineligibility reason (not just "Not eligible").

## Tier and Trend Rules

- Distance bands:
  - `> 5 km` -> green
  - `2..5 km` -> amber
  - `<= 2 km` -> red
- Distance hysteresis exits (anti-flicker):
  - red holds until `> 2.2 km` before distance-tier exits to amber,
  - amber holds until `> 5.3 km` before distance-tier exits to green.
- Vertical non-threat clamp:
  - if relative altitude is known and `|delta| >= 1200 m`, tier is green regardless of horizontal band.
- Closing hysteresis:
  - enter closing: `>= 1.0 m/s`
  - exit closing: `<= 0.3 m/s`
- Minimum trend sample delta: `800 ms`
- Recovery dwell before de-escalation to green: `4 s`
- Post-pass fresh sample thresholds:
  - red-to-green requires `2` fresh post-pass samples (`red -> amber -> green`)
  - amber-to-green requires `1` fresh post-pass sample
- First valid sample is alert-eligible until trend is established.

## Emergency Rules

Emergency is highest priority but only when all are true:

- distance `<= 1 km`
- inbound collision geometry match
- projected conflict remains likely within lookahead window when motion vectors are available
- explicit low-speed motion context (`< 3 m/s`) disables geometry emergency to avoid stationary/noise-driven false alerts
- actively closing
- relative altitude is available and inside configured above/below vertical gate
- sample fresh (`ageSec <= 20`, using max(received age, provider last-contact age when available))

If stale, not closing, or relative altitude is unavailable, emergency is disabled.

Emergency ineligibility reason contract:

- `no_ownship_reference`
- `not_closing`
- `trend_stale_waiting_for_fresh_sample`
- `stale_target_sample`
- `distance_outside_emergency_range`
- `relative_altitude_unavailable`
- `outside_vertical_gate`
- `target_track_unavailable`
- `heading_gate_failed`
- `motion_confidence_low`
- `projected_conflict_not_likely`
- `low_motion_speed`
- `vertical_non_threat`

## Ownship and Fallback Semantics

- With ownship reference:
  - full trend/tier/emergency policy applies.
- Same-coordinate ownship updates refresh ownship-reference freshness and republish snapshot/store state immediately (no wait for next network poll).
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
  - Runtime mutation ordering is single-writer and serialized on repository scope:
    all external mutators (`setEnabled`, center/ownship/filter updates, reconnect)
    are enqueued onto one runtime dispatcher lane before store/FSM mutation.
  - emergency-audio rollout master/shadow gates are sourced from ADS-B preferences SSOT.
- KPI accumulation (monotonic denominator, anti-nuisance counters, determinism guard):
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbEmergencyAudioKpiAccumulator.kt`
- Rollout + rollback controls SSOT:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficPreferencesRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbEmergencyAudioRolloutPort.kt`
- Runtime rollout + rollback gating:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimeSnapshot.kt`
- Map tier->color mapping:
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbProximityColorPolicy.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/AdsbGeoJsonMapper.kt`

## KPI Contract

- `alertsPerFlightHour`
  - numerator: `alertTriggerCount` from EMERGENCY FSM.
  - denominator: monotonic `activeObservationMs` while ADS-B is enabled with fresh ownship reference.
- `cooldownBlockEpisodesPerFlightHour`
  - numerator: `cooldownBlockEpisodeCount` from EMERGENCY FSM.
  - denominator: same monotonic `activeObservationMs`.
- `disableWithin5MinRate`
  - disable event: transition of effective EMERGENCY policy `enabled -> disabled`.
  - within-5min: disable event occurs <= 5 minutes after last alert trigger.
  - rate: `disableWithin5MinCount / disableEventCount`.
- `retriggerWithinCooldownCount`
  - invariant guard counter; increments if alert-trigger counter increases inside cooldown window.
- `determinismMismatchCount`
  - invariant guard counter; increments on monotonic time regressions or counter regressions in KPI path.

## Phase-6 Rollback Trigger Contract

Rollback latch triggers when any condition becomes true:
- `retriggerWithinCooldownCount > 0`
- `determinismMismatchCount > 0`
- `disableWithin5MinRate > 0.20` with `disableEventCount >= 2`

When latched:
- effective master rollout is forced off
- shadow mode telemetry can continue
- latch reason is surfaced in snapshot/debug and can be manually cleared from ADS-B settings

## Test Map

- Evaluator hardening:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbProximityTrendEvaluatorTest.kt`
- Store hardening:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreFilteringAndOrderingTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreTrendTransitionsTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreEmergencyGeometryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreCirclingEmergencyTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreTierHysteresisTest.kt`
- Repository deterministic transitions:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`
- KPI math + replay KPI parity:
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbEmergencyAudioKpiAccumulatorTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbEmergencyAudioReplayDeterminismTest.kt`

## Required Verification

Run for non-trivial proximity changes:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Automated phase runner (fast per phase, full at end):

```bat
scripts\qa\run_proximity_phase_gates.bat
```

## Related Docs

- `docs/ADS-b/ADSB.md`
- `docs/PROXIMITY/CHANGE_PLAN_ADSB_SMART_PROXIMITY_TREND_2026-03-01.md`
- `docs/PROXIMITY/CHANGE_PLAN_ADSB_EMERGENCY_AUDIO_ALERTS_2026-03-02.md`
- `docs/PROXIMITY/CHANGE_PLAN_ADSB_PROXIMITY_PRODUCTION_GRADE_2026-03-03.md`
- `docs/PROXIMITY/CHANGE_PLAN_ADSB_CIRCLING_1KM_RED_EMERGENCY_AUDIO_2026-03-04.md`
- `docs/PROXIMITY/CHANGE_PLAN_ADSB_TURN_AWARE_EMERGENCY_HARDENING_2026-03-06.md`
- `docs/PROXIMITY/AGENT_EXECUTION_CONTRACT_PROXIMITY_PHASED_FAST_THEN_FULL_2026-03-04.md`
