# LEVO_TE_ENERGY_COMPENSATION_IMPLEMENTATION_PLAN_2026-02-20.md

## 0) Metadata

- Title: Levo TE compensation hardening and airspeed/wind wiring completion
- Owner: XCPro Team
- Date: 2026-02-20
- Issue/PR: LEVO-20260220-TE-HARDENING
- Status: Draft

## 1) Scope

- Problem statement:
  - TE compensation is not reliably active in production flow.
  - Current TE kinetic term timing can be computed against emit cadence instead of speed-sample cadence, which is physically incorrect during pull-up/slowdown maneuvers.
  - External/replay airspeed paths exist but are not fully consumed by the TE/wind production pipeline.
  - Audio/UI consume adjacent vario channels with minor ordering mismatches.
- Why now:
  - Pilot-facing trust issue for pull-up/turn thermalling cases where stick-thermal suppression is expected.
  - Existing tests do not protect positive TE activation and cadence correctness.
- In scope:
  - TE activation gating and warm-up behavior.
  - TE kinetic compensation timing alignment to speed sample timebase.
  - External/replay airspeed wiring into metrics selection.
  - Wind fusion integration of EKF path where TAS is available.
  - Audio/UI alignment fixes for TE-driven outputs.
  - Unit/integration/replay regression tests.
  - Documentation sync in `docs/LEVO` and `docs/ARCHITECTURE/PIPELINE.md`.
- Out of scope:
  - Full redesign of variometer audio mapping policy.
  - New glider polar model or polar fitting changes.
  - New UI feature surfaces beyond consistency fixes.
- User-visible impact:
  - TE will engage consistently when valid airspeed data exists.
  - Pull-up slowdown events (for example 100 kt to 60 kt) stop presenting false lift as real thermal energy.
  - More consistent relation between numeric vario, needles, TE arc, and audio input channel.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| External live airspeed sample | `ExternalAirspeedRepository` | `AirspeedDataSource.airspeedFlow` | ViewModel/UI local airspeed caches |
| Replay airspeed sample | `ReplayAirspeedRepository` | `AirspeedDataSource.airspeedFlow` | Replay UI-side derived airspeed stores |
| Chosen TE airspeed estimate | `CalculateFlightMetricsUseCase` | `FlightMetricsResult.indicatedAirspeedMs/trueAirspeedMs/airspeedSourceLabel` | Re-selection logic in UI/manager |
| TE vario output | `CalculateFlightMetricsUseCase` | `FlightMetricsResult.teVario` -> `CompleteFlightData.teVario` | Recomputed TE in audio/UI |
| Audio input vario sample | `FlightDataCalculatorEngine` (`FlightDataEmissionState.latestAudioVario`) | `CompleteFlightData.audioVario` | Independent UI-side audio-input selection |
| Wind vector/confidence | `WindSensorFusionRepository` | `windState: StateFlow<WindState>` | Parallel wind stores in map/viewmodel |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/sensors/*`
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/*`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/*`
  - `feature/map/src/main/java/com/example/xcpro/audio/*`
  - `feature/map/src/main/java/com/example/xcpro/map/*`
  - `feature/map/src/test/java/com/example/xcpro/*`
- Any boundary risk:
  - Risk of moving business math into UI for visual alignment.
  - Risk of bypassing domain use-case by reading repositories directly in UI path.
  - Risk of domain coupling to Android/data types when adding airspeed timestamps.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| TE kinetic term timing source | `request.deltaTimeSeconds` in metrics use-case | Airspeed sample timestamp delta in metrics use-case | Preserve physics of `d(V^2/2g)/dt` against speed sample cadence | Unit tests for cadence mismatch scenario |
| TE warm-up/arming state | Implicit reset-to-zero branch | Explicit seeded TE state machine in use-case | Avoid unreachable TE activation from cold start | Positive TE activation test |
| Straight-flight wind from TAS | Unused `WindEkfUseCase` | `WindSensorFusionRepository` candidate path | Use available TAS/heading/g-load inputs already wired | Wind repository tests (EKF publish/drop cases) |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `CalculateFlightMetricsUseCase` TE branch | Falls back to non-TE due unreachable warm-up path | Explicit seed and hold previous eligible speed sample | Phase 1 |
| `CalculateFlightMetricsUseCase` TE dt | Uses emission dt independent of speed sample updates | Compute dt from speed sample clock deltas | Phase 1 |
| Wind fusion process | Ignores `input.airspeed` and `input.gLoad` in production | Integrate EKF candidate path with turn/g-load gating | Phase 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| `currentTimeMillis` in metrics request | Monotonic (live) / Replay sensor clock (replay) | Domain gating and validity windows |
| `wallTimeMillis` in metrics request | Wall | UI/reporting and calibration age only |
| `varioValidUntil` | Same as `currentTimeMillis` base | Validity comparison consistency |
| `gpsTimestampMillis` | Monotonic (live) / Replay clock (replay) | Speed sample event timing and circling windows |
| External/replay airspeed sample `clockMillis` | Monotonic (live) / Replay clock (replay) | TE kinetic term dt from true speed updates |
| TE compensation dt | Monotonic/replay sample-time delta | Physics-correct kinetic energy rate |
| `qnhCalibrationAgeSeconds` | Wall | Human-readable age semantics |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay clock vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - Fusion/metrics math on existing fusion loop context (`Default`-backed scope).
  - No additional blocking calls in map/UI threads.
- Primary cadence/gating sensor:
  - High-rate baro loop remains driver for vario emissions.
  - TE kinetic term must use speed sample timing, not baro frame interval.
- Hot-path latency budget:
  - Preserve TE-to-audio update target within current budget (typical <= 50 ms as per contributing guidance).

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: Yes in replay noise model, but seeded (`ReplaySimConfig.seed`) and therefore deterministic for same config/input.
- Replay/live divergence rules:
  - Replay uses IGC-derived simulation clock.
  - Live uses monotonic sensor clock.
  - Wall time remains output/metadata only.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| TE still unreachable after startup | SSOT/domain behavior correctness | Unit test | `CalculateFlightMetricsUseCaseTest` new positive TE activation case |
| TE dt uses wrong timebase | Timebase rules | Unit test | `CalculateFlightMetricsUseCaseTest` dt-source assertion case |
| Wind EKF path regression | Domain quality/uncertainty explicitness | Unit + integration tests | `WindSensorFusionRepositoryTest` EKF straight-flight cases |
| Audio uses stale TE sample | Pipeline behavior regression | Unit/integration test | `LevoVarioPipelineTest` audio/TE alignment case |
| Replay determinism drift | Determinism rule | Replay regression test | New replay determinism test (run twice, compare TE outputs) |
| UI misreports netto channel | UI render source consistency | Unit test | `FlightDataManager` flow mapping test |

## 3) Data Flow (Before -> After)

Before:

```
GPS + WindState -> WindEstimator.fromWind -> chosenAirspeed
chosenAirspeed + request.deltaTimeSeconds -> TE calc gate
TE often bypassed from cold start -> PRESSURE/BARO/GPS selected
Audio loop reads previous-frame latestTeVario -> audio input
```

After:

```
GPS + External/Replay TAS/IAS + WindState -> chosenAirspeed (priority: EXTERNAL -> WIND -> GPS fallback)
chosenAirspeed + speedSampleClockDelta -> TE kinetic compensation
Explicit TE warm-up state -> TE activates when eligible
Emitter publishes current TE first -> audio selection uses same-frame TE input
UI consumes corrected display/audio/netto channels consistently
```

## 4) Implementation Phases

### Phase 0: Baseline Lock and Failing Repro Tests

- Goal:
  - Freeze current behavior and add failing tests that represent known defects.
- Files to change:
  - `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/sensors/LevoVarioPipelineTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/weather/wind/WindSensorFusionRepositoryTest.kt`
- Tests to add/update:
  - Positive TE activation test from cold start (currently expected to fail).
  - TE dt-source test with GPS cadence mismatch (currently expected to fail).
  - Audio uses same-frame TE sample test (currently expected to fail).
- Exit criteria:
  - Failing tests clearly reproduce each target bug before implementation.

### Phase 1: TE Arming and Timebase Correction

- Goal:
  - Make TE mathematically and temporally correct for pull-up/slowdown compensation.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/FusionBlackboard.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/SensorFrontEnd.kt`
- Tests to add/update:
  - TE engages after seeded eligible airspeed samples.
  - TE uses speed-sample time delta, not emit-frame delta.
  - Regression test for 100 kt -> 60 kt slowdown scenario (expected sign and bounded magnitude behavior).
- Exit criteria:
  - TE source appears when eligible.
  - No unreachable TE branch from reset/cold start.
  - Pull-up slowdown test demonstrates stick-thermal suppression behavior.

### Phase 2: External and Replay Airspeed into Metrics Selection

- Goal:
  - Ensure valid IAS/TAS samples are consumable by TE path in production.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/data/ExternalAirspeedRepository.kt`
  - Live external-air data ingress adapter file(s) (to be identified during implementation scan)
- Tests to add/update:
  - Use-case prioritizes valid external/replay airspeed over wind-derived fallback.
  - Replay IAS/TAS fixture drives TE eligibility.
- Exit criteria:
  - TE can run from external/replay airspeed without wind dependency.
  - No UI/domain duplicate ownership introduced for airspeed state.

### Phase 3: Wind EKF Production Wiring

- Goal:
  - Use already-wired TAS/heading/g-load signals for straight-flight wind estimation.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/domain/WindEkfUseCase.kt` (only if API adjustments required)
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/model/WindState.kt` (if confidence/source semantics need extension)
- Tests to add/update:
  - EKF candidate published during straight flight with valid TAS.
  - EKF blackout during circling/high-turn/high-g events.
  - Source selection precedence remains intact (auto vs manual vs external).
- Exit criteria:
  - `input.airspeed` and `input.gLoad` are functionally consumed in production logic.
  - Wind confidence behavior remains explicit and stable.

### Phase 4: Audio and UI Channel Consistency

- Goal:
  - Remove one-frame TE lag in audio path and align displayed netto channel choice.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
- Tests to add/update:
  - Audio input selection reflects current-frame TE sample when available.
  - `nettoDisplayFlow` consumes intended display netto signal.
- Exit criteria:
  - Audio, TE arc, and selected vario streams are frame-consistent.
  - No business logic moved into Compose/UI layer.

### Phase 5: Documentation and Verification

- Goal:
  - Keep architecture docs synchronized with actual wiring and behavior.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/LEVO/levo.md`
  - `docs/LEVO/levo-replay.md`
  - `docs/LEVO/PurpleNeedle.md` (if audio channel behavior text changes)
- Tests to add/update:
  - None new beyond prior phases.
- Exit criteria:
  - Pipeline docs match implemented data path.
  - Replay/live timebase behavior explicitly documented.

## 5) Test Plan

- Unit tests:
  - `CalculateFlightMetricsUseCaseTest`:
    - TE activation positive case.
    - TE dt-source correctness under mixed baro/GPS cadence.
    - Pull-up slowdown compensation case (100 kt to 60 kt profile).
  - `SensorFrontEndTest`:
    - Airspeed hold timestamp behavior with new sample-time fields.
  - `WindSensorFusionRepositoryTest`:
    - EKF publish/drop behavior and source precedence.
- Replay/regression tests:
  - Extend replay validation to assert TE eligibility and deterministic outputs with IAS/TAS input.
  - Add replay deterministic double-run comparison for TE channel.
- UI/instrumentation tests (if needed):
  - Optional Compose integration test to verify displayed vario/netto channel mapping for one replay segment.
- Degraded/failure-mode tests:
  - Invalid or stale airspeed sample should fail open to wind/GPS fallback without NaN spikes.
  - Missing wind should not claim TE availability unless external airspeed is valid.
- Boundary tests for removed bypasses:
  - Ensure no reintroduction of emit-dt TE path.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| TE over/undershoot due dt mistakes | Pilot mistrust and wrong audio cues | Derive dt from speed sample timebase; clamp and unit tests | XCPro Team |
| External airspeed noise spikes | False TE transients | Validity/freshness gates and hold logic in domain | XCPro Team |
| EKF integration destabilizes wind source selection | Wind confidence flicker | Keep explicit selection precedence and add hysteresis tests | XCPro Team |
| Audio path refactor increases CPU load | Latency/perf regressions | Avoid duplicate metrics execution in loop; benchmark before/after | XCPro Team |
| Replay/live timebase drift in changes | Non-deterministic replay | Add deterministic double-run replay tests | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- TE activation is observable in positive eligible scenarios and absent in ineligible scenarios.
- TE kinetic compensation uses speed-sample timing, not emit-frame timing.
- Replay behavior remains deterministic for same input and config.
- Audio/UI channel alignment verified by tests on TE-enabled segments.
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry).

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 4 audio/UI consistency changes can be reverted without undoing TE math fixes.
  - Phase 3 EKF integration can be reverted while retaining Phase 1-2 TE/airspeed improvements.
  - Phase 2 external airspeed ingestion can be rolled back to wind-only TE estimation.
- Recovery steps if regression is detected:
  1. Revert latest phase commit only.
  2. Re-run `enforceRules`, unit tests, and replay determinism tests.
  3. Keep docs in sync with rolled-back state.
  4. Open follow-up issue with failing scenario and trace logs.

