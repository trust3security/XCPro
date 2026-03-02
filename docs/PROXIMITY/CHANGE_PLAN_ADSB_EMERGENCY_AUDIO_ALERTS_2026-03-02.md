# CHANGE_PLAN_ADSB_EMERGENCY_AUDIO_ALERTS_2026-03-02.md

## 0) Metadata

- Title: ADS-B EMERGENCY audio alerts (EMERGENCY only, no RED audio)
- Owner: XCPro Team
- Date: 2026-03-02
- Issue/PR: TBD
- Status: Production-ready upgrade in progress (Phases 1-4 complete; Phases 5-7 pending)

## 1) Scope

- Problem statement:
  - Visual EMERGENCY proximity exists, but audio confidence and rollout controls are not yet production-locked.
- Why now:
  - Phase 3 delivers functionality; this upgrade adds deterministic, operational, and rollout readiness required for public release.
- In scope:
  - EMERGENCY-only one-shot ADS-B audio.
  - FSM and anti-nuisance cooldown invariants.
  - Runtime settings and feature-flag rollout.
  - Deterministic replay evidence.
  - Telemetry KPIs, dashboards, and rollback criteria.
- Out of scope:
  - RED audio alerts.
  - Maneuver advisories.
  - OGN/vario audio policy changes.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Emergency audio FSM state and counters | `AdsbTrafficRepository` + `AdsbEmergencyAudioAlertFsm` | `AdsbTrafficSnapshot` telemetry fields | UI-local FSM or per-screen counters |
| Emergency audio settings (`enabled`, `cooldownMs`) | `AdsbTrafficPreferencesRepository` | Flow + settings VM state | transient UI-only copies used as policy authority |
| One-shot output trigger | repository decision path | `AdsbEmergencyAudioOutputPort` call | direct UI/composable sound calls |
| Rollout enable/shadow state | `AdsbEmergencyAudioFeatureFlags` | repository gate read | hardcoded per-call toggles |

### 2.2 Dependency Direction

Confirmed flow stays:

`UI -> ViewModel -> UseCase -> Repository/FSM -> Output Port Adapter`

No business policy in UI or adapter.

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| FSM transitions and cooldown | Monotonic | deterministic and stable anti-nuisance behavior |
| Replay validation timeline | Replay/monotonic simulation | deterministic parity across runs |
| UI labels only | Wall | presentation-only |

Forbidden:
- Monotonic vs wall comparisons.
- Replay vs wall comparisons.

### 2.4 Determinism

- Same ordered input sequence must produce identical trigger timeline and counters.
- No randomness in policy path.
- Adapter failures must not alter FSM decision state.

## 3) FSM Policy Contract (Non-Negotiable)

### 3.1 Eligibility

Alert eligibility is true only when all are true:
- Risk tier is `EMERGENCY`.
- Ownship reference is valid.
- Feature gate is on.
- User setting is enabled.

`RED` risk never triggers audio.

### 3.2 States

- `DISABLED`
- `IDLE`
- `ACTIVE`
- `COOLDOWN`

### 3.3 Required Transitions

- `DISABLED -> IDLE`: gates enabled.
- `IDLE -> ACTIVE`: eligible emergency entered, emit one alert.
- `ACTIVE -> COOLDOWN`: emergency cleared, start cooldown.
- `COOLDOWN -> ACTIVE`: cooldown elapsed and emergency present, emit one alert.
- `COOLDOWN -> IDLE`: cooldown elapsed and no emergency.
- `* -> DISABLED`: any gate disabled.
- `COOLDOWN -> COOLDOWN`: emergency present before cooldown expiry, block and count episode.

### 3.4 Anti-Nuisance Rules

- Cooldown default: `45_000 ms`.
- Allowed range: `15_000..180_000 ms`.
- One alert per eligible episode.
- Cooldown block counting is by contiguous episode, not per tick.

## 4) Production-Ready Definition

Feature is production-ready only when all are true:

1. Policy correctness
   - EMERGENCY-only path enforced in code and tests.
   - Cooldown re-trigger within cooldown is zero.
2. Determinism
   - Replay parity suite passes twice in a row with identical serialized traces.
3. Runtime safety
   - Adapter failure/focus denial paths are non-crashing and non-blocking.
4. Observability
   - KPI counters/events are emitted and visible on dashboard.
5. Rollout safety
   - Feature flags support shadow mode, partial rollout, and instant rollback.
6. Verification
   - `enforceRules`, `testDebugUnitTest`, `assembleDebug` all pass on release candidate.

## 5) Phased Implementation (Production-Ready Upgrade)

### Phase 0 - Contract lock (Completed, 2026-03-02)

- Deliverables:
  - Initial change plan and policy contract.

### Phase 1 - Pure FSM implementation (Completed, 2026-03-02)

- Deliverables:
  - `AdsbEmergencyAudioAlertFsm` + unit tests.

### Phase 2 - Repository wiring + settings ports + feature gates (Completed, 2026-03-02)

- Deliverables:
  - Repository decision ownership and snapshot telemetry.

### Phase 3 - Output adapter + settings UI (Completed, 2026-03-02)

- Deliverables:
  - Output port and Android adapter.
  - Runtime settings controls in ADS-B settings.

### Phase 4 - Deterministic replay lock (Completed, 2026-03-02)

- Goal:
  - Lock alert timeline determinism across replay and noise/churn scenarios.
- Delivered:
  - FSM hardening to suppress duplicate re-alert when settings are toggled OFF->ON during the same continuous emergency episode.
  - Deterministic replay matrix test suite (`R1..R8`) with serialized trace parity checks.
  - Full gate verification pass after implementation (`enforceRules`, `testDebugUnitTest`, `assembleDebug`).
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/adsb/AdsbEmergencyAudioAlertFsm.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/AdsbEmergencyAudioReplayDeterminismTest.kt`
- Exit criteria:
  - Zero determinism mismatches.
  - Zero cooldown-violation re-triggers.
  - Two consecutive clean runs in CI/local release loop.

### Phase 5 - Telemetry KPIs + dashboard + alerting (Pending, required for production)

- Goal:
  - Make rollout decisions data-driven and quickly reversible.
- Target score:
  - `>= 95/100` required to exit the phase.
- Deliverables:
  - KPI emission and dashboards for:
    - alerts/hour
    - cooldown-block episodes/hour
    - disable-within-5min rate
    - cooldown-violation count
    - determinism mismatch count
  - Alert rules for threshold breach.
- Exit criteria:
  - Dashboard populated in dogfood.
  - Breach alerts validated end-to-end.
  - Phase 5 scorecard result is `>= 95/100`.

### Phase 6 - Operational hardening and UX safety (Pending, required for production)

- Goal:
  - Ensure stable behavior under real device/audio constraints.
- Target score:
  - `>= 95/100` required to exit the phase.
- Deliverables:
  - Explicit handling validation for:
    - audio focus denied
    - adapter exception paths
    - rapid foreground/background transitions
    - repeated emergency churn
  - Connected/instrumented checks where applicable.
- Exit criteria:
  - No crash/ANR increase attributable to feature in internal cohort.
  - Confirmed non-blocking behavior when focus is denied.
  - Phase 6 scorecard result is `>= 95/100`.

### Phase 7 - Controlled rollout + rollback readiness (Pending, required for production)

- Goal:
  - Ship safely with hard go/no-go gates.
- Target score:
  - `>= 95/100` required to exit the phase.
- Rollout:
  1. `0%` master, shadow mode only.
  2. Internal dogfood (minimum 20 flight-hours).
  3. `5%`, then `25%`, then `50%`, then `100%` cohorts with hold gates between steps.
- Exit criteria:
  - All KPI thresholds within limits for each cohort.
  - Rollback kill-switch drill executed successfully.
  - Phase 7 scorecard result is `>= 95/100`.

## 6) KPI Gates (Go/No-Go)

Primary KPIs:
- `adsb_emergency_audio_alerts_per_flight_hour`
- `adsb_emergency_audio_cooldown_block_episodes_per_flight_hour`
- `adsb_emergency_audio_disable_within_5min_rate`
- `adsb_emergency_audio_retrigger_within_cooldown_count`
- `adsb_emergency_audio_determinism_mismatch_count`

Go criteria:
- cooldown retrigger count = `0`
- determinism mismatch count = `0`
- disable-within-5min rate <= `15%`
- no crash/ANR attributable increase vs baseline

Rollback criteria:
- disable-within-5min rate > `20%` for two consecutive cohorts
- cooldown retrigger count > `0`
- determinism mismatch count > `0`
- crash/ANR attributable delta exceeds release threshold

## 7) Rollback Runbook

1. Disable `adsbEmergencyAudioEnabled` immediately.
2. Keep shadow mode only if telemetry path is healthy.
3. Capture cohort/time-window evidence and failing KPI.
4. Revert last rollout increment or offending patch set.
5. Re-run required verification and replay parity matrix before re-enable.

## 8) Required Verification Commands

Minimum:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 9) Phase Tracking

- Phase 0: Completed
- Phase 1: Completed
- Phase 2: Completed
- Phase 3: Completed
- Phase 4: Completed
- Phase 5: Pending (required for production)
- Phase 6: Pending (required for production)
- Phase 7: Pending (required for production)

## 10) Immediate Next Step

Start Phase 5 now:
- wire KPI emission for emergency-audio rollout metrics,
- add dashboard/alert mapping and threshold checks,
- validate hold/rollback decision signals in dogfood.

## 11) Mandatory Scorecards for Phases 5-7 (Production Gate)

Phase closure rule:
- A phase is not complete unless:
  - all exit criteria pass, and
  - scorecard total is `>= 95/100`.

Scoring process:
- Score each criterion by evidence in code/tests/docs.
- Record score and evidence in this plan and PR notes.
- If any blocker applies, phase cannot exceed blocker cap even if raw points are higher.

### 11.1 Phase 5 Scorecard (Telemetry + Dashboard)

Criteria (100 total):
- KPI computation and emission complete for all 5 primary KPIs: 30
- Per-flight-hour denominator correctness and monotonic timebase compliance: 20
- Dashboard + threshold alert wiring validated end-to-end: 20
- Deterministic/replay and repository unit coverage for KPI math: 20
- Operator documentation (field meanings, hold gates, rollback mapping): 10

Blocker caps:
- Any missing primary KPI implementation: max 70
- Any KPI computed from wall-time in domain path: max 60
- `enforceRules` or `testDebugUnitTest` failing: max 60

### 11.2 Phase 6 Scorecard (Operational Hardening)

Criteria (100 total):
- Audio focus denied path proven non-crashing and non-blocking: 25
- Adapter exception containment with recovery evidence: 20
- Foreground/background churn resilience tests: 20
- Repeated emergency churn stress tests (no deadlocks/no missed state updates): 20
- Internal cohort crash/ANR comparison evidence vs baseline: 15

Blocker caps:
- Any crash attributable to emergency audio path in test/dogfood: max 50
- Any blocking/freeze risk on audio output path: max 60
- Missing connected/instrumented evidence where applicable: max 80

### 11.3 Phase 7 Scorecard (Rollout + Rollback Readiness)

Criteria (100 total):
- Runtime feature-flag control supports `0/5/25/50/100%` progression: 25
- Cohort hold-gate decisions are KPI-driven and documented: 20
- Rollback kill-switch drill executed and timestamped evidence captured: 25
- Rollback runbook validated by an actual rehearsal and re-verify pass: 20
- Release handoff package complete (owner, window, alert routing, on-call notes): 10

Blocker caps:
- No rollback drill evidence: max 70
- No cohort hold-gate evidence: max 70
- Any KPI breach ignored without halt/rollback action: max 60

### 11.4 Required Report Format (for each of Phases 5-7)

- Phase score: `NN/100`
- Evidence:
  - Code paths:
  - Tests:
  - Dashboards/alerts:
  - Rollout or drill logs:
- Blockers triggered:
- Decision: `PASS (>=95)` or `FAIL (<95)`
