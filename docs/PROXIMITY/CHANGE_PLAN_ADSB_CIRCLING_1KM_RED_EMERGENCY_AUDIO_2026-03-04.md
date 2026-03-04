# CHANGE_PLAN_ADSB_CIRCLING_1KM_RED_EMERGENCY_AUDIO_2026-03-04.md

## 0) Metadata

- Title: ADS-B circling-aware 1 km RED + emergency audio rule (production-grade rollout)
- Owner: XCPro Team
- Date: 2026-03-04
- Issue/PR: TBD
- Status: Draft
- Baseline score: `89/100`
- Target score: `>= 95/100`
- Non-negotiable code budget: `< 500 lines per production .kt file`

## 1) Scope

- Problem statement:
  - Current emergency behavior is geometry-driven and not explicitly gated by circling state + circling-enabled setting + stricter vertical cap.
- Why now:
  - You want a predictable high-salience rule during circling: nearby ADS-B traffic should become visually RED and produce emergency sound only when truly relevant.
- In scope:
  - New circling-aware emergency eligibility rule:
    - distance `<= 1_000 m`
    - ownship currently circling
    - circling feature enabled
    - vertical gate uses above/below settings plus hard cap `<= 1_000 ft` (`304.8 m`)
    - visual RED + emergency audio eligibility
  - FSM/audio integration, anti-nuisance hardening, deterministic tests, telemetry, rollout, rollback.
- Out of scope:
  - RED-audio for non-circling paths.
  - OGN behavior changes.
  - Advisory maneuver guidance.

## 2) Behavior Contract (New Rule)

Rule ID: `R-CIRCLING-RED-AUDIO-1KM`

Eligibility is true only when all are true:

1. `usesOwnshipReference == true` and ownship reference is fresh.
2. ADS-B target is fresh (`ageSec <= 20`).
3. Circling gate:
   - `ownshipIsCircling == true`
   - `circlingFeatureEnabled == true`
4. Horizontal distance `distanceMeters <= 1_000.0`.
5. Vertical delta passes both user filter and hard emergency cap:
   - `effectiveAboveMeters = min(verticalAboveMeters, 304.8)`
   - `effectiveBelowMeters = min(verticalBelowMeters, 304.8)`
   - `deltaMeters <= effectiveAboveMeters`
   - `-deltaMeters <= effectiveBelowMeters`
6. Target trend is closing (`AdsbProximityTrendEvaluator` authority).

Outputs for this rule:

- Visual tier forced to `RED`.
- Emergency audio eligibility set to true.

Priority with existing logic:

1. If this circling rule is true: visual is `RED` (as requested), audio eligible.
2. Else existing EMERGENCY geometry path remains unchanged.
3. Else existing trend-distance tiers remain unchanged.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Ownship circling state + freshness | `AdsbTrafficRepositoryRuntime` | internal runtime state + snapshot debug fields | UI-side policy booleans |
| Circling-enabled gate input | `AdsbTrafficRepositoryRuntime` (fed from thermalling settings path) | internal runtime state | ad-hoc reads inside store |
| Circling RED+audio eligibility decision | `AdsbTrafficStore` via dedicated policy | `AdsbTrafficUiModel` fields | UI recomputation |
| Emergency-audio FSM state/cooldown | `AdsbEmergencyAudioAlertFsm` in repository runtime | `AdsbTrafficSnapshot` | duplicate UI FSM |

### 3.2 Dependency Direction

Must remain:

`UI -> ViewModel -> UseCase -> Repository/Store/FSM -> Adapter`

No business policy in Compose/UI mapping.

### 3.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| ADS-B target freshness, trend, cooldown | Monotonic | deterministic safety behavior |
| Circling-state freshness timestamp in runtime | Monotonic | prevents stale circling-driven alerts |
| `lastContactEpochSec` age merge | Wall input converted to bounded age | provider freshness only |
| UI labels | Wall | presentation only |

Forbidden:
- Monotonic vs wall comparisons.
- Replay vs wall comparisons.

### 3.4 File Budget Guard

Mandatory hard gate:
- Every production Kotlin file touched by this implementation must end `< 500` lines.
- A phase cannot close if any touched production `.kt` file is `>= 500` lines.
- At `>= 450` lines, split before adding new logic.

Current slice hotspots:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimePolling.kt`: `500`
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt`: `485`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`: `472`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`: `416` (monitor)

Action:
- Split `AdsbTrafficRepositoryRuntimePolling.kt` before/with first behavior edit (for example move snapshot/audio publish helpers) so post-change is `< 500`.

## 4) Data Flow (Before -> After)

Before:

`Targets + ownship -> trend + geometry -> tier (incl. EMERGENCY) -> FSM uses EMERGENCY target -> audio`

After:

`Targets + ownship + circling state/settings -> trend + circling RED/audio policy + existing geometry policy -> visual tier + audio eligibility -> FSM -> audio`

## 5) Phased Implementation

Cross-phase exit gate:
- `LineBudgetGate`: all touched production `.kt` files remain `< 500` lines.
- Each phase summary must include a touched-file line-count evidence table.

### Phase 0 - Contract Lock + Baseline Replay Vectors

- Goal:
  - Freeze current behavior and define deterministic vectors for circling/non-circling edge cases.
- Files:
  - this plan doc
  - new replay vector doc under `docs/PROXIMITY/`
- Tests:
  - baseline trace capture for existing emergency + tier transitions.
- Exit criteria:
  - deterministic baseline vectors committed.
  - `LineBudgetGate` satisfied.

### Phase 1 - Pure Policy Logic (Domain/Store)

- Goal:
  - Implement circling RED+audio eligibility policy as pure logic in ADS-B domain/store path.
- Files (planned):
  - new policy file in `feature/map/src/main/java/com/example/xcpro/adsb/` (for example `AdsbCirclingEmergencyPolicy.kt`)
  - `AdsbTrafficStore.kt`
  - `AdsbTrafficModels.kt` (new explicit field for audio eligibility/reason)
- Tests:
  - distance threshold (`999/1000/1001 m`)
  - vertical cap via `304.8 m` and asymmetric above/below settings
  - circling enabled/disabled gates
  - closing vs non-closing
  - stale age rejection
- Exit criteria:
  - Rule `R-CIRCLING-RED-AUDIO-1KM` passes matrix and is deterministic.
  - `LineBudgetGate` satisfied.

### Phase 2 - Repository/UseCase/Wiring

- Goal:
  - Feed circling state + circling-enabled gate into repository SSOT without UI business logic.
- Files (planned):
  - `AdsbTrafficRepository.kt` (new update API for circling context)
  - `AdsbTrafficRepositoryRuntime.kt`
  - split `AdsbTrafficRepositoryRuntimePolling.kt` to maintain `<500` lines
  - `MapScreenUseCases.kt` (`AdsbTrafficUseCase`)
  - `MapScreenTrafficCoordinator.kt`
  - `MapScreenViewModelStateBuilders.kt` / `MapScreenViewModel.kt` (flow sourcing only)
- Tests:
  - runtime receives circling state changes and reselects deterministically
  - stale circling-context freshness suppresses rule
- Exit criteria:
  - end-to-end wiring active with no duplicate ownership.
  - `LineBudgetGate` satisfied.

### Phase 3 - FSM + Anti-Nuisance Audio Integration

- Goal:
  - Drive FSM from explicit audio-eligibility field (not visual color inference).
- Files (planned):
  - `AdsbTrafficRepositoryRuntimePolling.kt` (or extracted helper file)
  - `AdsbEmergencyAudioAlertFsm.kt` (only if transition semantics need adjustment)
- Tests:
  - one-shot alert on eligible entry
  - cooldown block behavior unchanged
  - OFF->ON during same continuous event remains non-duplicative
  - deterministic replay parity on timeline
- Exit criteria:
  - no cooldown violation retriggers; deterministic timeline unchanged for non-circling scenarios.
  - `LineBudgetGate` satisfied.

### Phase 4 - UI Truthfulness + Diagnostics

- Goal:
  - Ensure requested visual behavior (RED icon) and clear diagnostics.
- Files (planned):
  - `AdsbGeoJsonMapper.kt`
  - `AdsbMarkerDetailsSheet.kt`
  - `MapTrafficDebugPanels.kt`
  - `docs/PROXIMITY/README.md`
- Tests:
  - mapping tests for RED output on circling rule
  - details/debug reason-field assertions
- Exit criteria:
  - UI shows RED in requested scenario and does not imply different policy authority.
  - `LineBudgetGate` satisfied.

### Phase 5 - Deterministic Replay + KPI Telemetry

- Goal:
  - Production evidence for nuisance control and correctness.
- KPI set:
  - `adsb_circling_red_audio_trigger_count`
  - `adsb_circling_red_audio_cooldown_block_episode_count`
  - `adsb_circling_red_audio_disable_within_5min_rate`
  - `adsb_circling_red_audio_retrigger_within_cooldown_count`
  - `adsb_circling_red_audio_determinism_mismatch_count`
- Tests:
  - replay parity runs x2 on same vectors
  - KPI math unit tests with monotonic denominator
- Exit criteria:
  - all KPI fields emitted and deterministic.
  - `LineBudgetGate` satisfied.

### Phase 6 - Feature-Flag Rollout + Rollback Drill

- Goal:
  - Safe staged release with immediate kill-switch path.
- Rollout:
  1. `0%` master (shadow only)
  2. dogfood (`>=20` flight-hours)
  3. `5%` cohort
  4. `25%` cohort
  5. `50%` cohort
  6. `100%` cohort
- Rollback criteria:
  - `retrigger_within_cooldown_count > 0`
  - `determinism_mismatch_count > 0`
  - `disable_within_5min_rate > 20%` across two cohorts
  - crash/ANR attributable increase vs baseline
- Exit criteria:
  - rollback drill executed and evidence captured.
  - `LineBudgetGate` satisfied.

### Phase 7 - Production Signoff

- Goal:
  - Final score and release decision.
- Exit criteria:
  - score `>=95/100`
  - all required gates green
  - `LineBudgetGate` satisfied

## 6) Verification Plan

Required:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Too many alerts while circling in dense traffic | High | strict closing + vertical + freshness gating; cooldown telemetry gates | XCPro Team |
| Under-alerting due to aggressive vertical cap | Medium | bounded tuning with replay vectors and cohort hold gates | XCPro Team |
| Runtime file-size overflow (`>=500`) | Medium | mandatory pre-split of polling/runtime helper code | XCPro Team |
| Behavior confusion between EMERGENCY and RED | Medium | explicit rule reason fields in details/debug panels | XCPro Team |

## 8) Scoring Model (Production Gate)

Phase pass rule:
- Phases `4-7` require `>=95/100`.

Weights:
- Policy correctness + deterministic behavior: 25
- Anti-nuisance reliability (cooldown/no duplicate alerts): 20
- UI truthfulness for requested RED behavior: 15
- Telemetry/KPI + rollout/rollback evidence: 25
- Architecture compliance + line-budget compliance: 15

Blocker caps:
- Any touched `.kt` file `>=500` lines: max `0` (automatic phase fail)
- Any cooldown retrigger violation: max `60`
- Any determinism mismatch: max `60`
- Missing rollback drill evidence: max `75`

## 9) Definition of Done

- Requested scenario works exactly:
  - within 1 km,
  - circling now,
  - circling enabled,
  - vertical passes above/below and `<=1000 ft` cap,
  - icon RED,
  - emergency audio one-shot with cooldown.
- Required checks pass.
- Production score `>=95/100`.
- All touched production `.kt` files are `<500` lines with recorded evidence.

## 10) Genius Upgrade Track (Phase Scores > 92)

Quality objective:
- Every phase must score `>=92/100` before it is marked complete.

Current readiness snapshot (code-pass, 2026-03-04):
- Phase 0: `90/100`
- Phase 1: `74/100`
- Phase 2: `71/100`
- Phase 3: `73/100`
- Phase 4: `79/100`
- Phase 5: `76/100`
- Phase 6: `82/100`
- Phase 7: `84/100`

### 10.1 Phase 0 Uplift (90 -> >=92)

Improvements:
- Add committed replay vector catalog specific to circling/non-circling transitions.
- Add explicit baseline trace artifacts (tier timeline + audio timeline + settings timeline).
- Add phase evidence template that includes touched-file line counts.

Gate:
- No missing baseline vectors for any rule branch.

### 10.2 Phase 1 Uplift (74 -> >=92)

Improvements:
- Introduce dedicated policy model (for example `AdsbCirclingRedAudioEligibility`) so RED and audio eligibility are explicit and testable.
- Add exhaustive boundary tests:
  - horizontal: `999/1000/1001 m`
  - vertical cap: `304.7/304.8/304.9 m`
  - freshness: `19/20/21 s`
  - closing hysteresis enter/exit thresholds.
- Add deterministic double-run test for identical input sequence.

Gate:
- Full policy matrix passes, deterministic replay test passes twice.

### 10.3 Phase 2 Uplift (71 -> >=92)

Improvements:
- Split `AdsbTrafficRepositoryRuntimePolling.kt` before behavior edits (`500` line blocker).
- Add explicit repository API for circling context update (state + settings + freshness timestamp).
- Add runtime stale-circling guard (fail-safe false when stale).
- Add repository wiring tests for on/off transitions and stale context.

Gate:
- Circling context fully wired with no UI policy logic; `LineBudgetGate` passes.

### 10.4 Phase 3 Uplift (73 -> >=92)

Improvements:
- Decouple FSM trigger from `isEmergencyCollisionRisk`; use explicit audio eligibility field.
- Preserve anti-nuisance invariants:
  - one-shot per episode,
  - cooldown block episode counting,
  - OFF->ON no duplicate alert in continuous event.
- Add replay parity test including settings toggles and rapid state churn.

Gate:
- `retrigger_within_cooldown_count == 0` and determinism mismatches `== 0`.

### 10.5 Phase 4 Uplift (79 -> >=92)

Improvements:
- Add explicit rule-reason diagnostics (`circling_rule_applied`, `geometry_emergency_applied`).
- Ensure map color authority remains tier-driven only.
- Add UI tests verifying RED rendering and details/debug reason text for circling-triggered cases.

Gate:
- UI semantics tests pass for all policy branches.

### 10.6 Phase 5 Uplift (76 -> >=92)

Improvements:
- Add KPI accumulator with monotonic denominators and per-flight-hour normalization.
- Add KPI unit tests and deterministic replay KPI parity.
- Add alert thresholds + dashboard mapping document with operator interpretation notes.

Gate:
- All KPI fields emitted, validated, and documented.

### 10.7 Phase 6 Uplift (82 -> >=92)

Improvements:
- Add operational tests for:
  - audio focus denied,
  - adapter exception containment,
  - foreground/background churn,
  - rapid emergency churn.
- Add cohort evidence checklist (crash/ANR deltas, disable-within-5min behavior).

Gate:
- No feature-attributable crash/freeze regressions in dogfood window.

### 10.8 Phase 7 Uplift (84 -> >=92)

Improvements:
- Add formal rollout hold-gate checklist per cohort (`0/5/25/50/100`).
- Execute rollback drill and capture timestamped evidence with re-verify outputs.
- Add final production signoff packet (owner, on-call route, KPI dashboard links).

Gate:
- Rollback drill evidence complete and signoff packet approved.
