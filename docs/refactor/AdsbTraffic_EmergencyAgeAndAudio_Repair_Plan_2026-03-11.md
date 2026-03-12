# ADS-B Traffic Emergency Age and Audio Repair Plan

## 0) Metadata

- Title: ADS-B Traffic Emergency Age and Audio Repair
- Owner: Codex
- Date: 2026-03-11
- Issue/PR:
- Status: Draft

## 1) Scope

- Problem statement:
  - `./gradlew testDebugUnitTest` currently fails in `feature:traffic` with 3 ADS-B tests:
    - `AdsbTrafficStoreEmergencyGeometryTest.select_usesProviderLastContactAgeWhenOlderThanReceiveAge`
    - `AdsbTrafficStoreEmergencyGeometryTest.select_disablesEmergencyWhenClosingTargetAgeExceedsThreshold`
    - `AdsbTrafficRepositoryLifecycleAndEmergencyTest.emergencyAudio_emergencyOnly_staysActiveWithoutDuplicateTriggers`
- Why now:
  - Full unit-test verification is blocked.
  - The failures are in emergency-tier and emergency-audio behavior, which is release-critical for a paid application.
- In scope:
  - ADS-B target age semantics inside the traffic store.
  - Emergency gating age used for proximity tiering and emergency eligibility.
  - Emergency-audio continuity at the repository/FSM boundary.
  - Targeted ADS-B unit tests needed to lock the repaired behavior.
- Out of scope:
  - OGN behavior.
  - Map ownship adapter path.
  - UI wording/layout outside existing failing ADS-B tests.
  - Gradle config changes.
  - Broad refactors of `feature:traffic`.
- User-visible impact:
  - Correct stale-target handling for emergency tiering.
  - Correct emergency-audio continuity without duplicate alert retriggers.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B target raw timing (`receivedMonoMs`, `positionAgeAtReceiptSec`, `contactAgeAtReceiptSec`, `lastContactEpochSec`) | `AdsbTarget` in `AdsbTrafficStore` | internal store state | Recomputing alternative target age state in repository/UI |
| Effective target age used for emergency gating | `AdsbTrafficStore` | `AdsbTrafficUiModel.ageSec` and gating inputs | Parallel age-policy logic in repository/FSM |
| Ownship reference freshness | `AdsbTrafficRepositoryRuntime` | `usesOwnshipReference`, `referenceSampleMonoMs` | Store/FSM reimplementing ownship freshness policy |
| Emergency audio state | `AdsbEmergencyAudioAlertFsm` | snapshot telemetry/state | Repository-side duplicate alert state |
| Emergency audio candidate target ID | `AdsbTrafficStore` selection result | `emergencyAudioCandidateId` into repository snapshot path | Ad hoc candidate selection outside store |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/...`
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/...`
- Any boundary risk:
  - Low, as long as fixes remain inside the ADS-B store/repository package and do not leak policy into UI/map layers.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| None planned | - | - | This is a repair plan, not an ownership refactor | N/A |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| None planned | - | - | N/A |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| `positionAgeSec` | Monotonic | Derived from receipt monotonic time plus provider age-at-receipt |
| `contactAgeSec` | Monotonic + provider wall snapshot folded at receipt | Keeps age advancing deterministically after receipt |
| `lastContactEpochSec` | Wall | Provider metadata only; never compare directly to monotonic without conversion |
| Ownship reference freshness | Monotonic | Repository freshness gate |
| Emergency audio cooldown | Monotonic | Alert FSM cooldown gating |

Explicitly forbidden comparisons:

- Monotonic vs wall without conversion through age-at-receipt
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - `AdsbTrafficRepositoryRuntime` single-writer dispatcher remains authoritative.
- Primary cadence/gating sensor:
  - Existing ADS-B poll cadence and repository publish cadence remain unchanged.
- Hot-path latency budget:
  - No extra pass over targets beyond the existing store selection loop.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No new randomness
- Replay/live divergence rules:
  - No replay-specific behavior is introduced by this repair.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Age semantics drift between provider contact age and position age | Explicit time base / no hidden policy duplication | unit test | `AdsbTrafficStoreEmergencyGeometryTest` |
| Emergency tier remains active for stale targets | Explicit time base / deterministic store policy | unit test | `AdsbTrafficStoreEmergencyGeometryTest` |
| Emergency audio falls to cooldown during continuous emergency | SSOT + deterministic runtime behavior | unit test | `AdsbTrafficRepositoryLifecycleAndEmergencyTest` |
| Duplicate audio triggers after continuity fix | SSOT + FSM ownership | unit test | `AdsbTrafficRepositoryLifecycleAndEmergencyTest` |

### 2.7 Visual UX SLO Contract

- Not applicable.
- This repair does not change map/overlay/replay visual interaction wiring.

## 3) Data Flow (Before -> After)

Before:

```
Provider timing fields -> AdsbTarget -> AdsbTrafficStore computes positionAgeSec + contactAgeSec
-> store currently gates emergency using positionAgeSec only
-> repository consumes emergencyAudioCandidateId
-> emergency audio FSM may enter cooldown when candidate continuity drops
```

After:

```
Provider timing fields -> AdsbTarget -> AdsbTrafficStore computes explicit effective emergency age
-> store uses that age consistently for emergency gating
-> repository consumes stable emergencyAudioCandidateId semantics
-> emergency audio FSM remains ACTIVE through intended continuous-emergency scenarios
```

## 4) Implementation Phases

### Phase 0: Contract lock for repaired behavior

- Goal:
  - Confirm the intended contract before code changes:
    - emergency gating age must account for provider last-contact age when it is older than receive age
    - emergency audio must remain active through intended continuous-emergency sequences without duplicate triggers
- Files to change:
  - Only tests if one small clarifying assertion is needed; otherwise no production change
- Tests to add/update:
  - Keep the 3 currently failing tests as primary contract gates
  - Add one focused helper-level age test only if needed to remove ambiguity during repair
- Exit criteria:
  - Contract is explicit and no assertion weakening is needed

### Phase 1: Repair effective emergency age semantics in store

- Goal:
  - Make `AdsbTrafficStore` use the correct effective age for emergency gating and `ageSec` exposure where intended
- Files to change:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficThreatPolicies.kt` if a small helper is needed
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficModels.kt` only if naming must be clarified without semantic change
- Tests to add/update:
  - `AdsbTrafficStoreEmergencyGeometryTest`
  - Optional focused helper test if an explicit effective-age helper is introduced
- Exit criteria:
  - Both failing store tests pass
  - No unrelated ADS-B store tests regress
  - No new duplicated age-policy logic appears outside the store

### Phase 2: Repair emergency-audio continuity at repository/FSM boundary

- Goal:
  - Preserve continuous-emergency `ACTIVE` state without duplicate trigger count when the store still considers the situation continuous
- Files to change:
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimeSnapshot.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbEmergencyAudioAlertFsm.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt` only if candidate continuity semantics need a narrow fix there
- Tests to add/update:
  - `AdsbTrafficRepositoryLifecycleAndEmergencyTest`
  - Additional targeted FSM/repository test only if needed to lock the continuity rule
- Exit criteria:
  - `emergencyAudio_emergencyOnly_staysActiveWithoutDuplicateTriggers` passes
  - No duplicate trigger count regressions
  - No cooldown-block count regressions in continuous-emergency scenarios

### Phase 3: Verification closeout

- Goal:
  - Re-verify the ADS-B module and the full unit-test surface after the narrow repair
- Files to change:
  - None expected
- Tests to add/update:
  - None expected
- Exit criteria:
  - `:feature:traffic:testDebugUnitTest` passes
  - `testDebugUnitTest` passes

## 5) Test Plan

- Unit tests:
  - `AdsbTrafficStoreEmergencyGeometryTest`
  - `AdsbTrafficRepositoryLifecycleAndEmergencyTest`
  - Broader `:feature:traffic:testDebugUnitTest`
- Replay/regression tests:
  - Not applicable for replay-specific UI, but maintain deterministic clock-driven behavior in scheduler-based tests
- UI/instrumentation tests (if needed):
  - None planned
- Degraded/failure-mode tests:
  - Continuous emergency with one degraded/noisy sample
  - Stale closing target past emergency age threshold
  - Provider contact age older than receive age
- Boundary tests for removed bypasses:
  - None

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew :feature:traffic:testDebugUnitTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Changing `ageSec` semantics affects ordering/UI labels beyond emergency gating | Medium | Keep repair local; distinguish effective emergency age from display age if needed | Codex |
| Audio continuity fix introduces duplicate alert retriggers | High | Preserve trigger-count assertions and rerun emergency lifecycle tests first | Codex |
| Repair is applied in repository instead of store, creating duplicate age policy | High | Keep age-policy SSOT in `AdsbTrafficStore` only | Codex |
| Test weakening hides real defect | High | No assertion weakening for the 3 failing ADS-B tests | Codex |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- `KNOWN_DEVIATIONS.md` remains unchanged unless explicitly approved
- `:feature:traffic:testDebugUnitTest` passes
- `testDebugUnitTest` passes

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 1 store age repair
  - Phase 2 audio continuity repair
  - Any new targeted tests can remain if they encode accepted behavior
- Recovery steps if regression is detected:
  - Revert the most recent phase only
  - Re-run the 3 focused ADS-B failing tests
  - Re-run `:feature:traffic:testDebugUnitTest`

## 9) Plan Quality Score

- Score: 96/100
- Strengths:
  - Narrow scope
  - Directly tied to failing release-blocking tests
  - Explicit time-base handling
  - No architectural churn
- Residual risk:
  - The Phase 2 root cause may resolve either in store candidate continuity or repository/FSM integration; Phase 1 results should inform that exact patch point
