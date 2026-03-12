# CalculateFlightMetricsRuntime targeted refactor plan

## 0) Metadata

- Title: `CalculateFlightMetricsRuntime.kt` targeted line-budget and hot-path safety refactor
- Owner: XCPro Team
- Date: 2026-03-11
- Issue/PR: TBD
- Status: Draft

## 1) Executive recommendation

- Recommendation:
  - Proceed with a narrowed refactor only.
  - Do not pursue a full stage-by-stage breakup of the runtime.
  - If release is near, completing Phase 0 and stopping there is acceptable.
- Why:
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt` is too large for comfortable review, but it is also a high-risk hot path.
  - The runtime already delegates substantial stateful work to existing collaborators. The latest code pass confirms `FlightCalculationHelpers` already owns thermal tracking, netto warmup, AGL work scheduling, and L/D state, so extracting new thermal or output-assembly stages would add indirection with limited benefit.
  - The best production tradeoff is to protect behavior, make state ownership explicit, extract the two highest-value seams, and stop once the file is clearer and under `450` lines.
- Target:
  - `CalculateFlightMetricsRuntime.kt <= 450` lines
  - no algorithm change
  - no Gradle or DI changes
  - no replay/live semantic drift

## 2) Scope

- In scope:
  - characterization tests for runtime and downstream vario semantics
  - explicit runtime-local state holder
  - extraction of the airspeed policy seam
  - extraction of the glide-policy seam (`LevoNettoCalculator`, `AutoMcCalculator`, `SpeedToFlyCalculator` orchestration only)
  - low-risk hot-path cleanup:
    - counter storage
    - shared derived values
    - trivial allocation avoidance
- Out of scope:
  - no thermal-stage extraction
  - no sensor snapshot stage extraction
  - no output-assembler extraction
  - no threshold or policy tuning
  - no changes to UI, repository, or audio policy
  - no Gradle config changes

## 3) Code-pass findings that drive this reduced plan

### 3.1 What is already well-factored enough

- `FlightCalculationHelpers` already owns:
  - thermal tracker state
  - netto warmup and speed fallback state
  - AGL worker scheduling/state
  - L/D tracking state
- `SensorFrontEnd` already owns:
  - derivative history
  - pressure/GPS vario derivation
  - snapshot creation
- `FusionBlackboard` already owns:
  - average windows
  - netto display window
  - airspeed hold memory

Conclusion:
- extracting thermal, energy, or snapshot stages now would mostly wrap existing owners and increase coordination risk without a proportionate gain

### 3.2 What is still worth extracting

- Airspeed source selection is still a dense policy seam:
  - external freshness
  - wind candidate eligibility
  - GPS fallback
  - transition/event accounting
- Glide policy is still a coherent seam:
  - Levo netto orchestration
  - Auto-MC orchestration
  - STF orchestration

Conclusion:
- these are the highest-value extractions because they isolate branching policy without fighting existing state ownership

### 3.3 What must not be duplicated

- Single runtime-owned collaborators must remain single-instance:
  - `DisplayVarioSmoother`
  - `AirspeedSourceStabilityController`
  - `LevoNettoCalculator`
  - `AutoMcCalculator`
  - `SpeedToFlyCalculator`
- Existing owners must remain authoritative:
  - `SensorFrontEnd`
  - `FusionBlackboard`
  - `FlightCalculationHelpers`

### 3.4 Application-level sensitivity found in the pass

- `FlightDataManager` depends on exact `displayVario` and `varioValid` semantics for UI fallback and bucketing.
- `ResolveTrailVarioUseCase` depends on exact `displayNetto`, `nettoValid`, `baselineDisplayVario`, and replay vario priority.
- `ReplayFinishRamp` depends on `displayVario` being finite and crossing narrow thresholds near the end of replay.
- `FlightDisplayMapper` must remain a pure wiring layer; no smoothing/defaulting should leak into it.
- `VarioFrequencyMapper` is downstream audio mapping, not part of the runtime refactor target. This reduces the benefit of deeper runtime decomposition for audio concerns.

### 3.5 Re-pass correction: the current `FlightDataManager` Phase 0 seam is wrong

- The first Phase 0 attempt tried to prove `displayVario` policy through `FlightDataManager.displayVarioFlow`.
- Code pass result:
  - `displayVarioFlow` mixes two concerns:
    - semantic policy: select `displayVario` vs fallback `verticalSpeed`, then bucket
    - delivery mechanics: `throttleFrame(...)` plus `SharingStarted.WhileSubscribed(5_000)`
  - `throttleFrame(...)` already supports an injected clock parameter in `feature/map/src/main/java/com/example/xcpro/map/FlowThrottle.kt`.
  - `FlightDataManager` already has a shared support file, `feature/map/src/main/java/com/example/xcpro/map/FlightDataManagerSupport.kt`, which is the correct place for pure, deterministic UI-bucketing helpers.
  - `feature/map/src/test/java/com/example/xcpro/map/FlightDataManagerSupportTest.kt` already exists and directly tests support helpers in this slice. This is the correct no-churn place to add `displayVario` policy coverage.
  - `FlightDataManagerFactory` is the only production creation point, so any future test-only constructor seam is low churn.
  - `cardFlightDataFlow` already uses `toDisplayBucket(...)` and does not apply the `displayVario` fallback policy. That semantic difference must remain explicit.

Conclusion:
- release-grade Phase 0 should lock `displayVario` policy through a pure support/helper seam, not through the throttled manager flow
- manager flow timing is optional smoke coverage, not a must-have correctness gate

## 4) Architecture contract

### 4.1 SSOT and ownership

| Responsibility | Owner | Rule |
|---|---|---|
| authoritative metrics result | `CalculateFlightMetricsRuntime.execute(...)` | keep a single orchestration entrypoint |
| derivative and snapshot state | `SensorFrontEnd` | do not duplicate in new helpers |
| average windows and hold state | `FusionBlackboard` | do not move into runtime state holder |
| thermal/netto/AGL/LD helper state | `FlightCalculationHelpers` | do not split into new stage-owned state |
| display smoothing state | single `DisplayVarioSmoother` instance | do not create per-stage smoothers |
| airspeed transition state | single `AirspeedSourceStabilityController` instance | do not create parallel transition owners |
| Levo/Auto-MC/STF warm state | single runtime-owned calculator instances | do not reconstruct per call or per stage |

### 4.2 Timebase

- Preserve existing time semantics:
  - `currentTimeMillis`: monotonic live / replay simulation clock
  - `wallTimeMillis`: wall-time only where already used
  - `gpsTimestampMillis`: upstream sample clock
- Forbidden:
  - direct `System.currentTimeMillis`
  - `Instant.now()`
  - `Date()`
  - any new wall-clock comparison in domain logic

### 4.3 Dependency direction

Preserve:

`UI -> MapScreenViewModel -> FlightDataCalculatorEngine -> CalculateFlightMetricsUseCase -> CalculateFlightMetricsRuntime -> FlightMetricsResult -> FlightDataEmitter -> FlightDisplayMapper -> FlightDataRepository/UI consumers`

## 5) Target file split

- Keep:
  - `CalculateFlightMetricsRuntime.kt` as the orchestrator and owner of runtime collaborators
- Add only:
  - `FlightMetricsRuntimeState.kt`
  - `FlightMetricsAirspeedPolicy.kt`
  - `FlightMetricsGlidePolicy.kt`

Rules:
- same package only
- internal visibility unless existing API requires otherwise
- no new modules
- no new DI graph surface unless existing constructor wiring needs a minimal internal change

## 6) Phased plan

### Phase 0 - Characterization lock

- Goal:
  - lock current behavior before structural changes
- Must-have before any refactor:
  - add direct `FlightDisplayMapper` contract tests in `feature/map/src/test/java/com/example/xcpro/flightdata/FlightDisplayMapperTest.kt`
  - move `FlightDataManager` vario fallback/bucketing assertions to deterministic support-helper coverage in `feature/map/src/test/java/com/example/xcpro/map/FlightDataManagerSupportTest.kt`
  - add `ReplayFinishRamp` characterization tests in `feature/map/src/test/java/com/example/xcpro/replay/ReplayFinishRampTest.kt`
  - strengthen `CalculateFlightMetricsUseCaseWindPolicyTestRuntime.kt` to assert exact deterministic transition counts where practical, not only `>= 1`
  - extend `CalculateFlightMetricsUseCaseTestRuntime.kt` with use-case-level warm-state and reset tests for:
    - `AirspeedSourceStabilityController`
    - `LevoNettoCalculator`
    - `AutoMcCalculator`
    - `SpeedToFlyCalculator`
  - fix the false confidence in `LevoVarioPipelineTest.kt` by either:
    - adding real audio propagation coverage through the emitter/mapping path, or
    - narrowing the test name/scope so it no longer claims pipeline audio coverage
- Focus:
  - runtime math parity
  - downstream vario semantics that can regress silently
  - warm-state continuation and reset behavior
  - replay finish behavior near threshold boundaries
- Exit:
  - all Phase 0 must-haves are green
  - the behavior surface we care about is locked before extraction starts
  - Phase 1 does not begin until this gate is satisfied

### Phase 0A - Downstream test-seam correction for `FlightDataManager`

- Goal:
  - fix the test seam before continuing Phase 0 verification
- Implementation:
  - extract the current inline `displayVarioFlow` selection/fallback/bucket rule into an internal pure helper in `feature/map/src/main/java/com/example/xcpro/map/FlightDataManagerSupport.kt`
  - keep the rule byte-for-byte equivalent in behavior:
    - valid finite `displayVario` wins
    - invalid-but-finite `displayVario` above `VARIO_NOISE_FLOOR` still wins
    - otherwise finite `verticalSpeed` wins
    - otherwise `0`
    - final value buckets to `0.1 m/s`
  - make `displayVarioFlow` delegate to that helper and leave `throttleFrame(...)` / `SharingStarted.WhileSubscribed(5_000)` unchanged
  - add deterministic JVM tests for the helper in the existing `feature/map/src/test/java/com/example/xcpro/map/FlightDataManagerSupportTest.kt`
  - downgrade `FlightDataManagerVarioFlowTest.kt` to:
    - a minimal non-timing smoke test, or
    - removal if it adds no unique coverage after helper tests exist
- Explicit non-goals:
  - no production timing change
  - no change to `cardFlightDataFlow`
  - no attempt to unify `displayVarioFlow` semantics with card bucketing semantics
  - no new Gradle/Robolectric-only dependence for correctness tests
- Exit:
  - `displayVario` correctness is locked without clock/subscription coupling
  - `FlightDataManager` remains a UI-delivery owner, not the primary policy test seam

### Phase 1 - Make runtime-local state explicit

- Goal:
  - move only runtime-local mutable fields into `FlightMetricsRuntimeState`
- Move:
  - `prevTeSpeed`
  - ground-zero accumulation state
  - decision counters
  - transition counters
- Do not move:
  - anything from `SensorFrontEnd`
  - anything from `FusionBlackboard`
  - anything from `FlightCalculationHelpers`
  - any state inside `DisplayVarioSmoother`, `AirspeedSourceStabilityController`, `LevoNettoCalculator`, `AutoMcCalculator`, `SpeedToFlyCalculator`
- Exit:
  - reset behavior is unchanged
  - collaborator ownership is clearer
  - runtime line count drops modestly with no semantic change

### Phase 2 - Extract the airspeed policy seam

- Goal:
  - move airspeed decision orchestration into `FlightMetricsAirspeedPolicy`
- Extract:
  - external airspeed freshness checks
  - wind candidate resolution
  - GPS fallback selection
  - use of `AirspeedSourceStabilityController`
  - decision/transition event accounting inputs and outputs
- Keep in place:
  - `WindAirspeedEligibilityPolicy`
  - `AirspeedSourceStabilityController`
  - `FusionBlackboard` airspeed hold ownership
- Exit:
  - airspeed policy is isolated and reviewable
  - transition behavior is unchanged
  - no replay/live divergence introduced

### Phase 3 - Extract the glide-policy seam

- Goal:
  - move glide-policy orchestration into `FlightMetricsGlidePolicy`
- Extract orchestration only:
  - Levo netto call path
  - Auto-MC call path
  - STF call path
  - packaging of glide-policy outputs back to the runtime
- Keep in place:
  - single runtime-owned `LevoNettoCalculator`
  - single runtime-owned `AutoMcCalculator`
  - single runtime-owned `SpeedToFlyCalculator`
- Explicit non-goal:
  - do not split display smoothing, thermal tracking, or output mapping here
- Exit:
  - glide policy is isolated
  - warm-state calculators remain single-instance and reset identically

### Phase 4 - Low-risk hot-path cleanup

- Goal:
  - improve readability and reduce trivial overhead without algorithm change
- Allowed changes:
  - replace enum-keyed or map-backed counters with primitive storage where safe
  - compute shared timestamps/validity flags once per call
  - reduce repeated fallback calculations
  - avoid trivial per-call allocations where a local scalar or reusable structure is sufficient
- Explicitly defer:
  - synchronization changes
  - deeper object-shape redesign
  - stage explosion for line count
- Exit:
  - runtime is simpler
  - file is `<= 450` lines
  - no measurable behavioral drift

## 7) What this plan intentionally does not do

- It does not chase an aggressive `<= 320` target.
- It does not split thermal logic into another stage because that logic already lives behind `FlightCalculationHelpers`.
- It does not split sensor snapshot or TE preparation into another stage because `SensorFrontEnd` already provides the right seam.
- It does not extract output assembly because that would mostly move field wiring without reducing risk.

This is intentional. The goal is to improve the file where that buys something, not to maximize file count.

## 8) Test plan

### 8.1 Required domain and replay tests

- `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTestRuntime.kt`
- `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseWindPolicyTestRuntime.kt`
- `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseExternalAirspeedTestRuntime.kt`
- `feature/map/src/test/java/com/example/xcpro/sensors/LevoVarioPipelineTest.kt`
- `feature/map/src/test/java/com/example/xcpro/replay/IgcReplayLevoNettoValidationTest.kt`

### 8.2 Required downstream semantic tests

- `feature/map/src/test/java/com/example/xcpro/ConvertToRealTimeFlightDataTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/trail/ResolveTrailVarioUseCaseTest.kt`
- `feature/map/src/test/java/com/example/xcpro/flightdata/FlightDisplayMapperTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/FlightDataManagerSupportTest.kt`
- `feature/map/src/test/java/com/example/xcpro/replay/ReplayFinishRampTest.kt`

### 8.3 Phase 0 must-have checklist

- `FlightDisplayMapperTest.kt` must lock exact wiring for:
  - `displayVario`
  - `displayNeedleVario`
  - `displayNetto`
  - `nettoValid`
  - `baselineDisplayVario`
  - `realIgcVario`
  - `levoNetto`
  - `autoMc`
  - `speedToFly`
  - `audioVario`
- `FlightDataManagerSupportTest.kt` must cover:
  - valid finite `displayVario` wins
  - invalid-but-finite `displayVario` above noise floor still wins
  - invalid finite `displayVario` below noise floor falls back to `verticalSpeed`
  - non-finite `displayVario` falls back
  - null sample emits `0`
  - bucket behavior is stable at `0.1 m/s`
- `FlightDataManagerVarioFlowTest.kt` is optional smoke coverage only and must not be the primary semantic gate for those assertions
- `ReplayFinishRampTest.kt` must cover:
  - null/non-finite `displayVario` -> no ramp
  - `< 0.1 kt` -> no ramp
  - `0.1 kt .. 2.0 kt` -> ramp occurs
  - `> 2.0 kt` -> no ramp
  - sign of ramp matches source vario
  - replay vario is cleared at the end
- `CalculateFlightMetricsUseCaseWindPolicyTestRuntime.kt` must add:
  - exact transition-count assertions for one deterministic sequence
  - reset assertions for cleared decision and transition state
  - duplicate-event protection for `WIND_GRACE_HOLD` and `WIND_DWELL_BLOCK`
- `CalculateFlightMetricsUseCaseTestRuntime.kt` must add:
  - use-case-level warm-state continuation tests for `LevoNettoCalculator`, `AutoMcCalculator`, and `SpeedToFlyCalculator`
  - reset tests proving those warm-state collaborators restart cleanly
- `LevoVarioPipelineTest.kt` must be corrected so it does not imply runtime-to-audio wiring coverage unless it actually exercises that path

### 8.4 Nice-to-have before release

- add one short golden replay trace test asserting a small set of fields over a fixed sample sequence:
  - `displayVario`
  - `displayNetto`
  - `nettoValid`
  - `airspeedSourceLabel`
  - `levoNetto`
  - `speedToFlyValid`
- add one `FlightDataEmitter` integration-style test locking:
  - replay IGC vario override behavior
  - mapped audio vario propagation
  - `metrics.varioSource` to emitted frame consistency
- run device/emulator sanity checks for:
  - numeric vario
  - needle vario
  - replay finish behavior

### 8.5 Not needed yet

- no thermal-stage extraction tests beyond current helper coverage
- no output-assembler tests
- no benchmark/perf work until Phase 0 is green
- no deeper decomposition for line count alone

### 8.6 Required verification

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 9) Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| silent vario semantic drift | High | Phase 0 downstream characterization tests before extraction |
| brittle UI-flow timing tests hide or invent regressions | High | move `displayVario` assertions to a pure helper seam; keep timed manager flow out of must-have correctness gates |
| duplicate warm-state helper instances | High | keep calculators/smoothers/controller single-owned by runtime |
| replay end behavior shifts due to tiny display-vario drift | Medium | targeted finish-ramp threshold test |
| over-refactor for line count | High | stop at airspeed + glide seams only |
| state moved to wrong owner | High | keep `SensorFrontEnd`, `FusionBlackboard`, and `FlightCalculationHelpers` authoritative |
| compile-safe but behavior-unsafe cleanup | Medium | only low-risk cleanup after seams are locked by tests |

## 10) Acceptance criteria

- `CalculateFlightMetricsRuntime.kt <= 450` lines
- runtime is still the single orchestration entrypoint
- no new Gradle config changes
- no new timebase violations
- no duplicate state owners introduced
- Phase 1 does not begin until all Phase 0 must-haves are green
- `displayVario` Phase 0 correctness must be locked through a deterministic support/helper test, not only through `FlightDataManager` timed-flow behavior
- airspeed policy is extracted and behaviorally identical
- glide-policy orchestration is extracted and behaviorally identical
- `displayVario`, `displayNetto`, `nettoValid`, `baselineDisplayVario`, and replay vario semantics remain unchanged for downstream consumers
- replay determinism remains unchanged for identical input sequences
- required checks pass

## 11) Rollback plan

- Each phase is independently reversible:
  - revert `FlightMetricsRuntimeState.kt`
  - revert `FlightMetricsAirspeedPolicy.kt`
  - revert `FlightMetricsGlidePolicy.kt`
  - revert low-risk cleanup separately
- If regression appears:
  - revert the last phase only
  - rerun targeted domain/replay/downstream tests
  - narrow the seam instead of adding more helper layers

## 12) Plan quality score

- Score: 96/100
- Why:
  - limited to the seams that actually improve maintainability
  - avoids low-value decomposition around helpers that already own the real state
  - explicitly protects downstream vario semantics, replay behavior, and warm-state continuity
  - corrected the `FlightDataManager` Phase 0 seam to a deterministic support-helper contract instead of a brittle timed-flow test
  - line target is realistic for this file and this risk profile
- Remaining risk:
  - some missing Phase 0 downstream tests still need to be added before extraction starts
  - optional manager-flow smoke coverage may still need a low-churn constructor seam later if direct timing assertions are considered worthwhile
