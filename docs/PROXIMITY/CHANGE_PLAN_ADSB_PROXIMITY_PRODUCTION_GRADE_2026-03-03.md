# CHANGE_PLAN_ADSB_PROXIMITY_PRODUCTION_GRADE_2026-03-03.md

## 0) Metadata

- Title: ADS-B proximity production-grade hardening (false/misleading alert reduction + safe rollout)
- Owner: XCPro Team
- Date: 2026-03-03
- Issue/PR: TBD
- Status: Draft
- Baseline score: `84/100`
- Target score: `>= 95/100`
- Non-negotiable code budget: `< 500 lines per production .kt file`

## 1) Scope

- Problem statement:
  - Current smart proximity is effective, but still exposes nuisance/misleading risk in edge conditions (first-sample escalation, sparse updates, source latency uncertainty), and lacks full production telemetry/rollout evidence.
- Why now:
  - The feature is close to release quality and needs a structured hardening path to become production-safe.
- In scope:
  - Proximity risk-policy hardening to reduce false/misleading alerts.
  - Predictive conflict modeling for escalation confidence (time-to-conflict and projected miss-distance).
  - Source-quality and freshness gating for safer escalation decisions.
  - EMERGENCY-audio production rollout controls and observability.
  - KPI-driven rollout/rollback with dogfood evidence.
  - Strict architecture compliance and Kotlin file-size governance.
- Out of scope:
  - RED audio alerts.
  - Maneuver/advisory guidance.
  - OGN policy redesign.

## 1.1 Production Gate Targets

Primary targets to claim production grade:

- `adsb_proximity_false_or_misleading_alert_rate_per_flight_hour <= 0.20`
- `adsb_proximity_deescalation_lag_after_divergence_ms_p95 <= 5_000`
- `adsb_emergency_audio_retrigger_within_cooldown_count = 0`
- `adsb_emergency_audio_determinism_mismatch_count = 0`
- `adsb_emergency_audio_disable_within_5min_rate <= 15%`
- No feature-attributable crash/ANR increase vs baseline.

Note:
- Exact threshold tuning is locked in Phase 0 using replay corpus + dogfood baseline.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Target trend memory and closure state | `AdsbProximityTrendEvaluator` in `AdsbTrafficStore` path | `AdsbTrafficUiModel` fields | UI-local trend state |
| Predictive conflict metrics (time-to-conflict, miss-distance) | New domain policy owner (`AdsbProximityRiskPolicy`) | derived tier + debug fields | ad-hoc UI calculations |
| Final proximity tier | `AdsbTrafficStore` | `AdsbTrafficUiModel.proximityTier` | map expression thresholds as authority |
| EMERGENCY audio FSM state and counters | `AdsbEmergencyAudioAlertFsm` via repository runtime | `AdsbTrafficSnapshot` | UI FSM replicas |
| Runtime rollout controls (master/shadow/cohort) | DI-injected feature-gate owner | repository gate reads | hardcoded booleans at callsites |
| KPI aggregates | repository-side accumulator | snapshot/debug telemetry + dashboard feed | per-screen counters |

### 2.2 Dependency Direction

Must remain:

`UI -> ViewModel -> UseCase -> Repository/Store/FSM -> Adapter`

Constraints:
- No business risk-policy logic in UI composables.
- No Android/framework types in domain policy classes.
- Repository/runtime owns policy orchestration.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Escalation confidence for RED/EMERGENCY | Distance/trend-only store policy | Predictive conflict policy in domain path | reduce nuisance from non-threatening geometry | replay/unit matrix |
| First-sample escalation handling | implicit alert-eligible default | explicit probation rule in policy | avoid misleading initial spikes | deterministic tests |
| KPI normalization and gate signals | implicit raw counters | explicit KPI accumulator | rollout safety and objective decisions | KPI tests + dashboard checks |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| UI color expression interpreting raw geometry as policy | render path can be seen as authority boundary | keep UI as tier->color only and add tests | Phase 3 |
| Runtime rollout toggles via hardcoded defaults | test-only mutation patterns | runtime control owner with explicit cohort config | Phase 4 |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Trend closure and dwell transitions | Monotonic | deterministic anti-flicker |
| Predictive conflict windows | Monotonic | replay parity and stable math |
| Audio cooldown and re-trigger gating | Monotonic | anti-nuisance invariant |
| KPI per-hour denominator | Monotonic flight-active interval | avoids wall-time drift |
| UI labels | Wall | presentation only |

Forbidden:
- Monotonic vs wall comparisons.
- Replay vs wall comparisons.

### 2.4 Determinism and Replay

- Same ordered replay input must produce identical:
  - proximity tier timeline,
  - EMERGENCY timeline,
  - audio trigger timeline,
  - KPI counter timeline.
- No randomness in policy path.
- Adapter failures must not mutate policy state.

### 2.5 File-Size and Modularity Contract (`<500 lines per .kt`)

Global rule:
- Every production Kotlin file touched by this work must stay `< 500` lines.

Execution rules:
- Soft threshold: at `>= 450` lines, split before adding new logic.
- Hard threshold: build cannot be considered phase-complete if any touched file is `>= 500`.
- Preferred split style:
  - policy math in dedicated domain files,
  - repository orchestration in runtime files,
  - UI composables split by concern (content, sections, helpers).

Current near-cap files in this slice:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt` (`424`)
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimePolling.kt` (`438`)
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/AdsbSettingsScreen.kt` (`460`)

Preemptive actions:
- No new feature logic added to near-cap files without extraction in the same phase.

## 3) Data Flow (Before -> After)

Before:

`Raw ADS-B -> Trend evaluator -> Distance tier + emergency geometry -> UI color + audio FSM`

After:

`Raw ADS-B -> Trend evaluator -> Predictive conflict policy + quality gating -> Final tier/EMERGENCY -> Audio FSM -> KPI accumulator -> Snapshot/debug/dashboard -> Cohort gate decisions`

## 4) Phased Implementation

### Phase 0 - Baseline lock and replay corpus

- Goal:
  - Lock baseline behavior and create deterministic replay vectors for nuisance/misleading scenarios.
- Files:
  - `docs/PROXIMITY/CHANGE_PLAN_ADSB_PROXIMITY_PRODUCTION_GRADE_2026-03-03.md`
  - new replay-vector doc under `docs/PROXIMITY/`
  - targeted tests in `feature/map/src/test/java/com/example/xcpro/adsb/`
- Tests:
  - baseline sequence snapshots for approach/diverge/crossing/sparse/stale/duplicate-like patterns.
- Exit criteria:
  - baseline score and target thresholds documented.
  - replay corpus committed and deterministic.

### Phase 1 - Predictive conflict policy (false-alert reduction core)

- Goal:
  - Add predictive escalation confidence so RED/EMERGENCY require stronger conflict evidence than distance alone.
- Files:
  - new domain policy files in `feature/map/src/main/java/com/example/xcpro/adsb/`
  - `AdsbTrafficStore.kt` integration (kept under line budget)
- Tests:
  - projected miss-distance and time-to-conflict test matrix.
  - regression tests proving non-threatening geometries do not escalate.
- Exit criteria:
  - RED/EMERGENCY tied to predictive conflict confidence.
  - no determinism regressions.

### Phase 2 - Quality/freshness gating and first-sample probation

- Goal:
  - Reduce misleading escalations under stale/sparse/uncertain target quality.
- Files:
  - policy/gating classes under `adsb/`
  - `AdsbTrafficStore.kt` and related model fields
- Tests:
  - stale-age gating,
  - ownship-missing behavior,
  - first-sample probation and minimum sample-consistency rules,
  - jitter/noise anti-flicker.
- Exit criteria:
  - no immediate high-severity escalation from low-confidence first sample unless emergency-confidence threshold is met.

### Phase 3 - Tier semantics and UI truthfulness hardening

- Goal:
  - Ensure UI never overstates confidence and remains policy-faithful.
- Files:
  - `AdsbProximityColorPolicy.kt`
  - `AdsbGeoJsonMapper.kt`
  - details/debug UI files
- Tests:
  - mapper/policy tests,
  - details-screen semantics tests.
- Exit criteria:
  - UI is tier-driven only and does not recompute policy.
  - explicit low-confidence/advisory semantics where required.

### Phase 4 - EMERGENCY audio runtime controls and line-budget-safe wiring

- Goal:
  - Make EMERGENCY audio rollout controls production-operable (master/shadow/cohort) without line-budget drift.
- Files:
  - feature-flag owner and repository runtime wiring files
  - split near-cap files before adding logic if needed
- Tests:
  - master-off/shadow-on/master-on,
  - cohort gate behavior,
  - OFF->ON continuity invariants.
- Exit criteria:
  - runtime controls operable without restart,
  - behavior matches gate contract,
  - all touched files remain `<500` lines.

### Phase 5 - KPI accumulation, dashboards, and threshold alerts

- Goal:
  - Operationalize go/no-go metrics for production decisions.
- Files:
  - repository KPI accumulator + snapshot fields
  - debug/dashboard mapping docs/config
- Tests:
  - deterministic KPI math and threshold breach tests.
- Exit criteria:
  - all primary KPIs emitted and visible.
  - breach alert path validated end-to-end.

### Phase 6 - Operational hardening (device/audio/lifecycle)

- Goal:
  - Prove stable, non-blocking behavior under device edge conditions.
- Files:
  - audio adapter + runtime lifecycle handling
  - optional instrumentation helpers
- Tests:
  - audio focus denied,
  - adapter exception path,
  - rapid foreground/background churn,
  - repeated emergency churn.
- Exit criteria:
  - no crash/freeze attributable to feature in internal validation.

### Phase 7 - Controlled rollout and rollback drill

- Goal:
  - Release safely with cohort hold gates and verified rollback.
- Rollout order:
  1. `0%` master, shadow only.
  2. dogfood (`>=20` flight-hours).
  3. `5%` cohort.
  4. `25%` cohort.
  5. `50%` cohort.
  6. `100%` cohort.
- Exit criteria:
  - each cohort passes go criteria before promotion.
  - rollback drill executed successfully with captured evidence.

### Phase 8 - Production signoff and post-release guardrails

- Goal:
  - Final quality rescore and ongoing guardrails.
- Deliverables:
  - final score report,
  - residual-risk register,
  - post-release monitor checklist.
- Exit criteria:
  - proximity slice score `>=95/100`.
  - all acceptance gates met.

## 5) Scoring Model and Pass Gates

Scoring rule:
- Phases 4-8 require `>=95/100` to pass.

### 5.1 Scorecard Weights

| Criterion | Weight |
|---|---:|
| Policy correctness and determinism | 20 |
| False/misleading alert reduction evidence | 20 |
| Runtime rollout and rollback safety | 20 |
| KPI observability and alerting quality | 20 |
| Compliance + verification + line-budget adherence | 20 |

### 5.2 Blocker Caps

- Any touched production `.kt` file `>=500` lines: max `70/100`
- Missing runtime rollout control path: max `75/100`
- Missing any primary KPI: max `70/100`
- Any cooldown retrigger violation: max `60/100`
- Any determinism mismatch: max `60/100`
- No rollback drill evidence: max `80/100`

## 6) KPI Gates (Go/No-Go)

Primary KPIs:
- `adsb_emergency_audio_alerts_per_flight_hour`
- `adsb_emergency_audio_cooldown_block_episodes_per_flight_hour`
- `adsb_emergency_audio_disable_within_5min_rate`
- `adsb_emergency_audio_retrigger_within_cooldown_count`
- `adsb_emergency_audio_determinism_mismatch_count`
- `adsb_proximity_false_or_misleading_alert_rate_per_flight_hour`
- `adsb_proximity_deescalation_lag_after_divergence_ms_p95`

Go criteria:
- cooldown retrigger count = `0`
- determinism mismatch count = `0`
- disable-within-5min rate <= `15%`
- false/misleading alert rate <= target threshold
- no crash/ANR attributable increase vs baseline

Rollback criteria:
- disable-within-5min rate > `20%` across two consecutive cohorts
- cooldown retrigger count > `0`
- determinism mismatch count > `0`
- false/misleading alert rate exceeds threshold for two consecutive cohorts
- crash/ANR attributable increase above release threshold

## 7) Test Plan

- Unit tests:
  - trend/tier correctness,
  - predictive conflict policy,
  - freshness/quality gating,
  - emergency-audio FSM invariants,
  - KPI math.
- Replay/regression tests:
  - parity double-run for tier/audio/KPI timelines.
  - nuisance-focused vector set.
- Instrumented tests when relevant:
  - audio focus/lifecycle behavior,
  - foreground/background transitions.

Required verification:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Execution cadence (mandatory):

1. Per phase (basic gate):
   - `./gradlew enforceRules`
   - targeted tests for the changed proximity slice
   - module build for touched module(s), typically `:feature:map:assembleDebug`
2. At integration boundaries (every 1-2 phases):
   - `./gradlew testDebugUnitTest`
3. Final phase-complete/full gate:
   - `python scripts/arch_gate.py`
   - `./gradlew enforceRules`
   - `./gradlew testDebugUnitTest`
   - `./gradlew assembleDebug`

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Windows resilience when test-result files are locked:

```bat
test-safe.bat :feature:map:testDebugUnitTest
```

## 8) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Over-suppression hides meaningful alerts | High | threshold tuning on replay corpus + staged rollout gates | XCPro Team |
| Policy complexity increases maintenance burden | Medium | strict modular split and line-budget governance | XCPro Team |
| Runtime flag drift across environments | Medium | centralized gate owner + explicit tests | XCPro Team |
| KPI misinterpretation by operators | Medium | clear KPI definitions and dashboard legend docs | XCPro Team |
| File-size growth causes monolith drift | Medium | 450 soft split threshold and hard 500 cap | XCPro Team |

## 9) Rollback Plan

1. Disable emergency-audio master flag immediately.
2. Keep shadow mode only if telemetry path remains healthy.
3. Capture failing cohort window and KPI evidence.
4. Revert rollout increment or offending patch set.
5. Re-run required verification and replay parity before re-enable.

## 10) Definition of Done

- Proximity production score `>=95/100`.
- Phases 0-8 completed with evidence.
- No architecture-rule violations.
- No touched production Kotlin files at or above 500 lines.
- Required verification gates pass.
