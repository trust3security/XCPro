# LEVO_TE_ENERGY_COMPENSATION_IMPLEMENTATION_PLAN_2026-02-20.md

## 0) Metadata

- Title: Levo TE compensation hardening and airspeed/wind wiring completion
- Owner: XCPro Team
- Date: 2026-02-20
- Issue/PR: LEVO-20260220-TE-HARDENING
- Status: In Progress (Phase 2 item 1 and item 2 implemented on 2026-02-22)

## 0A) Progress Update (2026-02-22)

Completed in code and tests:
- External/replay airspeed is now wired into metrics path:
  `SensorFusionRepositoryFactory -> FlightDataCalculator -> FlightDataCalculatorEngine -> FlightDataEmitter -> FlightMetricsRequest`.
- `CalculateFlightMetricsUseCase` now prioritizes airspeed as:
  `SENSOR (fresh+valid external/replay) -> WIND (quality-gated) -> GPS fallback`.
- `AirspeedSource.EXTERNAL` is now emitted (`airspeedSourceLabel = "SENSOR"`).
- Dead path `WindEstimator.fromPolarSink()` removed.
- Density ratio now uses QNH in:
  - `WindEstimator.computeDensityRatio(altitude, qnh)`
  - `ReplaySampleEmitter` IAS/TAS single-source reconstruction path.

Still open:
- Live external ingress adapter/callsite into `ExternalAirspeedRepository.updateAirspeed(...)`
  remains pending (outside this patch set).

## 0B) Current Execution Snapshot (2026-02-22)

1. Done
- External/replay airspeed is wired into TE metrics path and selected as
  `SENSOR -> WIND -> GPS` with freshness/validity gates.
- QNH-aware density ratio is active in both wind estimator and replay IAS/TAS reconstruction.
- Dead `fromPolarSink` path removed.

2. Next
- Add the missing live ingress callsite into
  `ExternalAirspeedRepository.updateAirspeed(...)` so real live TAS/IAS can drive TE.

3. Then
- Integrate/confirm straight-flight EKF wind path while preserving strict confidence gating
  and deterministic fallback behavior.

4. Validation
- Keep replay + unit regressions green after each step.
- Required checks:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

5. Tracking
- Update this plan document and `docs/ARCHITECTURE/PIPELINE.md` in the same change
  whenever TE/airspeed/wind pipeline wiring changes.

## 1) Scope

- Problem statement:
  - TE compensation is not reliably active in production flow.
  - Current TE kinetic term timing can be computed against emit cadence instead of speed-sample cadence, which is physically incorrect during pull-up/slowdown maneuvers.
  - External/replay airspeed paths exist in repositories but are not consumed by the TE metrics path; current TE selection remains wind-derived TAS or GPS fallback.
  - Live external airspeed ingress currently has no discovered callsite into `ExternalAirspeedRepository.updateAirspeed(...)`.
  - GPS fallback airspeed currently overwrites previously held energy-eligible airspeed state, which collapses TE continuity across short wind-data gaps.
  - Wind-derived TAS for TE has no explicit confidence threshold; low-confidence wind can still drive TE compensation.
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
| Metrics-ready airspeed sample/timebase | `FlightDataCalculatorEngine` -> `FlightDataEmitter` request | `FlightMetricsRequest` (new airspeed payload field(s)) | Pulling repos directly from `CalculateFlightMetricsUseCase` or UI |
| Chosen TE airspeed estimate | `CalculateFlightMetricsUseCase` | `FlightMetricsResult.indicatedAirspeedMs/trueAirspeedMs/airspeedSourceLabel` | Re-selection logic in UI/manager |
| TE vario output | `CalculateFlightMetricsUseCase` | `FlightMetricsResult.teVario` -> `CompleteFlightData.teVario` | Recomputed TE in audio/UI |
| Audio input vario sample | `FlightDataCalculatorEngine` (`FlightDataEmissionState.latestAudioVario`) | `CompleteFlightData.audioVario` | Independent UI-side audio-input selection |
| Wind vector/confidence | `WindSensorFusionRepository` | `windState: StateFlow<WindState>` | Parallel wind stores in map/viewmodel |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/*`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/*`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/*`
  - `feature/map/src/main/java/com/trust3/xcpro/audio/*`
  - `feature/map/src/main/java/com/trust3/xcpro/map/*`
  - `feature/map/src/test/java/com/trust3/xcpro/*`
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
| `SensorFusionRepositoryFactory` / `FlightDataCalculator` / `FlightDataCalculatorEngine` | No airspeed dependency in metrics pipeline constructor chain | Inject source-aware `AirspeedDataSource` and forward sample/timestamp into metrics request | Phase 2 |
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
| Eligible airspeed hold overwritten by GPS fallback | Domain correctness / fallback behavior | Unit test | `FusionBlackboardTest` + `SensorFrontEndTest` fallback-overwrite cases |
| TE engages from low-confidence wind vectors | Domain quality handling | Unit test | `CalculateFlightMetricsUseCaseTest` wind-confidence gating case |
| External/replay TAS never reaches metrics | SSOT wiring and dependency direction | Integration/unit test | `FlightDataCalculatorEngine` + `FlightDataEmitter` wiring tests |
| Live external TAS ingress missing | SSOT completeness | Integration test + code review gate | New ingress adapter test with `ExternalAirspeedRepository` updates |
| Wind EKF path regression | Domain quality/uncertainty explicitness | Unit + integration tests | `WindSensorFusionRepositoryTest` EKF straight-flight cases |
| Audio uses stale TE sample | Pipeline behavior regression | Unit/integration test | `LevoVarioPipelineTest` audio/TE alignment case |
| Replay determinism drift | Determinism rule | Replay regression test | New replay determinism test (run twice, compare TE outputs) |
| UI misreports netto channel | UI render source consistency | Unit test | `FlightDataManager` flow mapping test |

### 2.7 Recursive Deep-Pass Delta (2026-02-21)

| Confirmed Gap | Evidence | Impact | Planned Fix Hook |
|---|---|---|---|
| Metrics path cannot read repository airspeed | `CalculateFlightMetricsUseCase` chooses `airspeedFromWind ?: GPS_GROUND` only; request has no airspeed payload fields | TE unavailable when wind estimate missing even if TAS exists in repos | Add airspeed payload and timestamp to `FlightMetricsRequest`; prioritize external/replay TAS before wind fallback |
| Fusion engine constructor chain lacks airspeed dependency | `SensorFusionRepositoryFactory` -> `FlightDataCalculator` -> `FlightDataCalculatorEngine` currently takes no `AirspeedDataSource` | Structural block: external/replay TAS never reaches metrics | Extend factory/engine constructors with source-aware airspeed flow and pass through to emitter/use-case |
| Live external ingress is absent | `ExternalAirspeedRepository.updateAirspeed(...)` has no callsites | Live TE from external vario cannot activate | Add explicit ingress adapter and DI binding; define freshness/validity policy |
| GPS fallback overwrites held eligible airspeed | `resolveAirspeedHold()` always remembers non-null sample; use-case always provides GPS fallback when wind estimate missing | Short wind dropouts immediately remove TE-eligible hold and reset TE warm-up | Preserve last eligible estimate when new sample source is non-eligible fallback, or separate hold lanes by source quality |
| TE path has no wind confidence gate | Use-case passes `windState.vector` directly to wind estimator without confidence threshold | Low-confidence wind can inject TAS error into TE compensation | Gate wind-derived TAS with explicit confidence/quality threshold before TE eligibility |
| Wind EKF exists but is orphaned | `WindEkfUseCase` present; `WindSensorFusionRepository` does not call it | No straight-flight TAS-assisted wind estimation | Integrate EKF candidate into `WindStore`/selection path with turn/g-load/blackout gates |
| Audio update can lag one frame behind TE | `audioController.update(emissionState.latestTeVario, ...)` executes before `FlightDataEmitter` refreshes `latestTeVario` | Audible mismatch vs displayed TE source in transitions | Reorder loop or compute current-frame TE before audio selection |
| UI netto display flow maps raw netto | `FlightDataManager.nettoDisplayFlow` uses `it?.netto` instead of `displayNetto` | UI inconsistency vs display pipeline smoothing intent | Map to `displayNetto` and add mapping regression test |
| Existing test encodes missing EKF behavior | `WindSensorFusionRepositoryTest` currently asserts no straight-flight wind | Feature work can regress silently if test intent is not updated | Replace with positive EKF straight-flight publish test once Phase 3 lands |
| `AirspeedSource.EXTERNAL` is never emitted | Only enum declaration exists; no constructor path currently yields EXTERNAL in metrics | UI/source labels and TE diagnostics cannot reflect true external source usage | Add explicit external sample mapping in metrics selection and source-label regression tests |
| `WindEstimator.fromPolarSink()` is dead path | Function exists but has no production callsite | Hidden complexity with no runtime validation and stale test-only coverage | Either wire intentionally with clear eligibility semantics or remove/deprecate it |
| Density ratio currently ignores QNH input | `WindEstimator.computeDensityRatio(..., qnhHpa)` does not use `qnhHpa` | Small but systematic IAS/TAS conversion mismatch under non-standard pressure | Decide and document whether to use pressure altitude only or include QNH-adjusted model; add unit tests |

### 2.8 Recursive Deep-Pass Delta (2026-02-22)

| Confirmed Gap | Evidence | Impact | Planned Fix Hook |
|---|---|---|---|
| TE and wind-speed switches are wired to the wrong settings keys | `LevoVarioSettingsScreen.VarioDisplayOptionsCard`: "Wind speed" row binds `teCompensationEnabled`; "Total Energy compensation" row binds `showWindSpeedOnVario` | Pilot-facing controls are misleading; TE may be toggled unintentionally while pilot expects wind-label toggle (and vice versa) | Swap `checked`/`onCheckedChange` bindings to match labels; add UI wiring regression test |
| TE wind gating still bypasses `WindState.isAvailable` semantics | `CalculateFlightMetricsUseCase` passes `windState?.vector` directly to `WindEstimator.fromWind(...)` | Stale/invalid wind vectors can feed TE speed estimate despite `WindState.stale`/quality semantics | Gate TE wind path on `windState?.isAvailable == true` plus confidence threshold, then fallback |
| Levo netto filter constants are fixed and sailplane-biased | `LevoNettoCalculator`: `WINDOW_METERS = 600.0`, `MIN_GLIDE_SPEED_MS = 12.0`, `MIN_WINDOW_SPEED_MS = 8.0` | Low-speed aircraft get laggy or invalid netto behavior; tuning mismatch vs paraglider/hang-glider regimes | Add aircraft-profile-aware speed gate/window parameters (v1 from profile/polar bounds; optional slider later) |
| Replay IAS/TAS density conversion path is still standard-atmosphere only | `ReplaySampleEmitter.computeDensityRatio(altitudeMeters)` has no `qnhHpa` input | Replay IAS/TAS derived from single-source IAS or TAS can diverge under non-standard pressure scenarios | Decide explicit replay policy (pressure-altitude-only vs QNH-adjusted) and enforce with unit tests/docs |
| Path drift in agent-facing docs caused onboarding/read-order failures | `AGENTS.md` and `docs/LEVO/levo-replay.md` previously referenced `docs/LevoVario/...` while repo path is `docs/LEVO/...` | Mandatory read-order instructions could fail for new contributors/agents | Fixed in 2026-02-22 doc pass by normalizing references to `docs/LEVO/...` |

## 3) Data Flow (Before -> After)

Before:

```
GPS + WindState -> WindEstimator.fromWind -> chosenAirspeed
External/Replay airspeed repositories -> used by FlightState/Wind inputs only (not metrics TE request)
chosenAirspeed + request.deltaTimeSeconds -> TE calc gate
TE often bypassed from cold start -> PRESSURE/BARO/GPS selected
Audio loop reads previous-frame latestTeVario -> audio input
```

After:

```
GPS + External/Replay TAS/IAS + WindState -> chosenAirspeed (priority: EXTERNAL/REPLAY -> WIND -> GPS fallback)
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
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/LevoVarioPipelineTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/weather/wind/WindSensorFusionRepositoryTest.kt`
- Tests to add/update:
  - Positive TE activation test from cold start (currently expected to fail).
  - TE dt-source test with GPS cadence mismatch (currently expected to fail).
  - Audio uses same-frame TE sample test (currently expected to fail).
  - Straight-flight TAS EKF expectation test (currently expected to fail).
  - Fallback-overwrite test: eligible airspeed hold must survive non-eligible GPS fallback intervals.
- Exit criteria:
  - Failing tests clearly reproduce each target bug before implementation.

### Phase 1: TE Arming and Timebase Correction

- Goal:
  - Make TE mathematically and temporally correct for pull-up/slowdown compensation.
  - Align TE/netto eligibility behavior with explicit wind availability semantics and aircraft-speed envelopes.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/AirspeedModels.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FusionBlackboard.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/SensorFrontEnd.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/LevoNettoCalculator.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/WindEstimator.kt` (if speed timestamp/source propagation is added to estimate model)
- Tests to add/update:
  - TE engages after seeded eligible airspeed samples.
  - TE uses speed-sample time delta, not emit-frame delta.
  - Regression test for 100 kt -> 60 kt slowdown scenario (expected sign and bounded magnitude behavior).
  - Wind-confidence gating test for TE eligibility.
  - Wind-availability gating test (`windState.isAvailable`) for TE eligibility.
  - Hold-behavior test where GPS fallback does not clobber eligible hold state.
  - Low-speed aircraft regression test for Levo netto window/gate behavior (no extreme lag/invalid lockout in PG envelope).
- Exit criteria:
  - TE source appears when eligible.
  - No unreachable TE branch from reset/cold start.
  - Pull-up slowdown test demonstrates stick-thermal suppression behavior.

### Phase 2: External and Replay Airspeed into Metrics Selection

- Goal:
  - Ensure valid IAS/TAS samples are consumable by TE path in production.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorFusionRepositoryFactory.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculator.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngine.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataEmitter.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/ExternalAirspeedRepository.kt`
  - Live external-air data ingress adapter file(s) (new; no current callsite found)
- Tests to add/update:
  - Use-case prioritizes valid external/replay airspeed over wind-derived fallback.
  - Replay IAS/TAS fixture drives TE eligibility.
  - Engine-level test: active-source-aware airspeed feed remains consistent for LIVE vs REPLAY.
  - Source-label test: metrics emit `airspeedSourceLabel = "SENSOR"` for external airspeed path.
- Exit criteria:
  - TE can run from external/replay airspeed without wind dependency.
  - No UI/domain duplicate ownership introduced for airspeed state.

### Phase 3: Wind EKF Production Wiring

- Goal:
  - Use already-wired TAS/heading/g-load signals for straight-flight wind estimation.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/data/WindSensorFusionRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/domain/WindEkfUseCase.kt` (only if API adjustments required)
  - `feature/map/src/main/java/com/trust3/xcpro/weather/wind/model/WindState.kt` (if confidence/source semantics need extension)
- Tests to add/update:
  - EKF candidate published during straight flight with valid TAS.
  - EKF blackout during circling/high-turn/high-g events.
  - Source selection precedence remains intact (auto vs manual vs external).
  - Existing negative straight-flight test replaced with positive EKF expectation plus gating-specific negatives.
- Exit criteria:
  - `input.airspeed` and `input.gLoad` are functionally consumed in production logic.
  - Wind confidence behavior remains explicit and stable.

### Phase 4: Audio and UI Channel Consistency

- Goal:
  - Remove one-frame TE lag in audio path and align displayed netto channel and settings-control mapping.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataEmitter.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/FlightDataManager.kt`
- Tests to add/update:
  - Audio input selection reflects current-frame TE sample when available.
  - `nettoDisplayFlow` consumes intended display netto signal.
  - Settings screen regression test: TE toggle and wind-speed toggle each mutate the intended preference key.
- Exit criteria:
  - Audio, TE arc, and selected vario streams are frame-consistent.
  - No business logic moved into Compose/UI layer.

### Phase 5: Documentation and Verification

- Goal:
  - Keep architecture docs synchronized with actual wiring and behavior.
  - Remove documentation drift where docs imply airspeed/EKF paths are active before they are production-wired.
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
    - External/replay airspeed priority over wind-derived estimate.
    - TE wind-confidence gating case.
    - TE wind-availability gating case (`WindState.isAvailable` respected).
  - `SensorFrontEndTest`:
    - Airspeed hold timestamp behavior with new sample-time fields.
    - Hold survival when fallback GPS samples are present between eligible estimates.
  - `FusionBlackboardTest`:
    - Non-eligible fallback samples do not overwrite eligible hold lane.
  - `LevoNettoCalculatorTest`:
    - Aircraft-profile-driven low-speed envelope test (PG/HG range) with bounded response lag.
  - `FlightDataCalculatorEngine` / `FlightDataEmitter` tests:
    - Active-source-aware airspeed wiring into metrics request.
    - External source-label propagation (`SENSOR`) once external path is wired.
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
  - Replay IAS/TAS single-source conversion behavior remains deterministic and policy-consistent under non-standard QNH scenarios.
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

