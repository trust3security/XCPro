# CalculateFlightMetricsRuntime phased split implementation plan

## 0) Metadata

- Title: `CalculateFlightMetricsRuntime.kt` release-grade phased split plan
- Owner: Codex
- Date: 2026-03-12
- Issue/PR: TBD
- Status: In Progress
- Progress note:
  - 2026-03-12: Phase 0 targeted JVM characterization tests implemented for display helper reset coverage, runtime display reset/ground-zero behavior, and external-airspeed fallback edge cases.
  - 2026-03-12: Focused Phase 1 deep pass corrected the stale line-count snapshot and tightened sole-owner, delegation, and direct-test constraints so the split cannot degrade into forwarding churn.
  - 2026-03-12: Phase 1 implemented. `FlightMetricsDisplayRuntime` now owns display smoother, baseline smoother, both needle channels, and ground-zero state; `CalculateFlightMetricsRuntime.kt` is down to `392` lines and direct helper tests were added.
- Driver:
  - pre-Phase-1 snapshot: `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt` was `469` lines
  - current post-Phase-1 snapshot: `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt` is `392` lines
  - correction from the earlier plan: the refactor used a fresh line-count snapshot, not the stale `506`-line figure
  - the latest polar slice added `polarLdCurrentSpeed` and `polarBestLd`, which increased pressure but did not change the core ownership model
- Goal:
  - keep behavior identical
  - reduce `CalculateFlightMetricsRuntime.kt` to `<= 400` lines
  - avoid ad hoc breakup, DI churn, or hidden ownership changes

## 1) Scope

- Problem statement:
  - `CalculateFlightMetricsRuntime.kt` currently mixes four different concerns in one file:
    - airspeed source resolution and diagnostics
    - display smoothing / needle / ground-zero state
    - main runtime orchestration
    - large result assembly
  - the hot path is still coherent as one entrypoint, but the file is now too large to absorb new work safely
- Why now:
  - the file is still oversized for the next planned work even after falling back under the hard gate
  - upcoming glide-target / final-glide work should not land on top of a file that is already oversized
  - the best time to split is before more responsibilities accumulate
- In scope:
  - behavior-locking characterization where gaps still exist
  - same-package internal split only
  - extraction of the two seams that buy the most line-count relief and review clarity
  - optional tiny local cleanup if needed after the two real seams are out
- Out of scope:
  - no algorithm tuning
  - no final-glide or task-glide work
  - no module split
  - no UI/repository changes
  - no ownership moves out of `SensorFrontEnd`, `FusionBlackboard`, `FlightCalculationHelpers`, or the existing glide calculators
  - no thermal-stage split

## 2) Focused deep-pass corrections

### 2.1 What the earlier plan overstated

The earlier plan treated several Phase 0 tasks as still missing. After a focused code pass, these are already present:

- `FlightDataManagerSupportTest.kt` already covers the display-vario fallback helper seam
- `ReplayFinishRampTest.kt` already covers:
  - non-finite display vario
  - low-threshold no-ramp
  - positive ramp
  - negative ramp
  - high-threshold no-ramp
  - final clear
- `CalculateFlightMetricsUseCaseWindPolicyTestRuntime.kt` already covers:
  - exact decision counts
  - exact transition counts for a single episode
  - reset clearing of decision/transition counters
- `CalculateFlightMetricsUseCaseTestRuntime.kt` already covers reset parity for:
  - `LevoNettoCalculator`
  - `SpeedToFlyCalculator`
  - `AutoMcCalculator`
- `LevoVarioPipelineTest.kt` has already been narrowed so it does not falsely claim end-to-end audio propagation coverage

Conclusion:
- those items should be removed from the must-add Phase 0 list

### 2.2 What is actually still missing

The real remaining Phase 0 gaps are narrower:

- low-level display helper coverage is only partial:
  - `DisplayVarioSmootherTest.kt` covers `smoothVario(...)` clamp/invalid-decay but does not cover `smoothNetto(...)`
  - `DisplayVarioSmootherTest.kt` does not lock `reset()` clearing both internal channels
  - `NeedleVarioDynamicsTest.kt` covers response/invalid-decay but does not lock `reset()`
- runtime-level display characterization is still thin:
  - no direct test locks the ground-zero settling rule
  - no direct test proves `useCase.reset()` clears the primary display smoother state, not just the baseline smoother
  - no direct test proves `useCase.reset()` clears both needle states
- external-airspeed integration coverage is still incomplete:
  - no test for `timestampMillis` freshness fallback when `clockMillis` is absent or stale
  - no test for invalid external samples being ignored
  - no test for `indicatedMs` fallback to `trueMs` when indicated airspeed is invalid
- there is still no dedicated integration test around `FlightDataEmitter`, but that is a nice-to-have, not a Phase 0 must-have, for this split

### 2.3 Split strategy corrections

After the code pass, two earlier planned files should be downgraded or removed:

- `FlightMetricsRuntimeState.kt` is probably not worth a dedicated file
  - once display state moves into a display helper and airspeed counters move into an airspeed resolver, the only notable runtime-local scalar left is `prevTeSpeed`
  - creating a dedicated state-holder file for that would add churn without real payoff
- `FlightMetricsResultAssembler.kt` should not be a default phase
  - the `FlightMetricsResult(...)` block is large, but it is stable, dumb wiring
  - after the two real extractions, the runtime should likely already land below target
  - if any extra reduction is still needed, prefer a small private `buildResult(...)` helper inside the runtime file before creating another class

### 2.4 Best seam choices after the deep pass

- Extract first: `FlightMetricsDisplayRuntime`
  - because it owns real state and removes both field bulk and inline execute-body bulk
  - it must own:
    - the shared `DisplayVarioSmoother` used for `displayVario` and `displayNetto`
    - the separate baseline smoother
    - both `NeedleVarioDynamics` instances
    - ground-zero accumulation state
    - reset behavior for those stateful display elements
  - it must preserve exact operation order:
    - smooth primary display vario first
    - then apply ground-zero settling only to the primary display vario
    - smooth baseline display vario independently
    - smooth display netto from `FusionBlackboard`'s `displayNettoRaw`
    - drive both needle channels directly from `bruttoVario`
  - it must preserve exact input sources:
    - ground-zero uses `gps.speed.value`, not IAS/TAS
    - needle targets use `bruttoVario`, not `displayVario`
    - baseline display uses `snapshot.baselineVario`, not `bruttoVario`
    - display netto uses `averages.displayNettoRaw` plus `nettoResult.valid`
  - it must stay lifecycle-stable:
    - one runtime-owned helper instance only
    - no helper creation inside `execute(...)`
    - no extra synchronization inside the helper; the existing runtime/use-case boundary remains the synchronization owner
  - it must not introduce policy duplication:
    - keep using the existing `FlightMetricsConstants` values
    - do not create a second constants holder for display/needle tuning
    - if ground-zero thresholds move, keep them private to the helper file rather than widening shared surface
  - it must become the sole display-state owner after extraction:
    - remove `displaySmoother` from the runtime
    - remove `baselineDisplaySmoother` from the runtime
    - remove `needleDynamics` from the runtime
    - remove `fastNeedleDynamics` from the runtime
    - remove `groundZeroAccumulatedSeconds` from the runtime
    - remove the runtime display wrapper methods rather than forwarding through them
    - runtime `reset()` must delegate to one helper `reset()` call instead of clearing display state in multiple places
  - it needs a minimal surface:
    - one `update(...)` entrypoint returning display outputs
    - one `reset()` entrypoint
    - no getters, callbacks, flows, counters, or configuration-holder objects in this phase
  - expected line-budget effect:
    - Phase 1 should remove the current display-state field block, the execute-body display block, the runtime display helper methods, and the private ground-zero constants/imports
    - implemented result: the runtime landed at `392` lines, so the line-budget target is already met after Phase 1
- Extract second: `FlightMetricsAirspeedResolver`
  - because it removes the branch-heavy airspeed selection logic and the diagnostic bookkeeping
  - it should own:
    - `WindAirspeedEligibilityPolicy`
    - `AirspeedSourceStabilityController`
    - decision / transition counters
    - external/GPS fallback helpers
    - reset and counter accessors
- Do not split:
  - thermal update logic
  - `SensorFrontEnd`
  - `FusionBlackboard`
  - glide calculators
  - `FlightMetricsResult` into another top-level assembler by default

## 3) Architecture contract

### 3.1 SSOT ownership

| Responsibility | Owner | Exposed as | Forbidden duplicates |
|---|---|---|---|
| overall runtime orchestration | `CalculateFlightMetricsRuntime.execute(...)` | `FlightMetricsResult` | parallel runtime entrypoints |
| snapshot and derivative state | `SensorFrontEnd` | `SensorSnapshot` | runtime/helper duplicate derivative caches |
| rolling averages and netto display window | `FusionBlackboard` | average outputs | helper-owned average history |
| thermal, AGL, and measured L/D state | `FlightCalculationHelpers` | helper outputs | copied thermal or AGL state in new helpers |
| polar sink / L-D lookups | `StillAirSinkProvider` | `sinkAtSpeed`, `ldAtSpeed`, `bestLd`, `iasBoundsMs` | helper-side polar caches |
| display smoothing / needle / ground-zero state | `FlightMetricsDisplayRuntime` after Phase 1 | display outputs | second smoother/needle owners |
| wind eligibility, source stability, and transition counters | `FlightMetricsAirspeedResolver` after Phase 2 | chosen airspeed and diagnostics | duplicate controller / duplicate counters |
| Levo/Auto-MC/STF warm state | existing calculator instances | glide outputs | per-call recreation of calculators |

### 3.2 Dependency direction

Required flow remains:

`UI -> domain -> data`

This refactor stays inside the same domain/runtime package. No new upward dependencies are allowed.

### 3.2A Boundary moves

| Responsibility | Old owner | New owner | Why | Validation |
|---|---|---|---|---|
| display smoothing and needle orchestration | `CalculateFlightMetricsRuntime` | `FlightMetricsDisplayRuntime` | isolate true stateful display behavior | display/runtime characterization tests |
| ground-zero settling | `CalculateFlightMetricsRuntime` | `FlightMetricsDisplayRuntime` | keep all display state in one owner | on-ground settle tests |
| wind eligibility, source stability, and counter bookkeeping | `CalculateFlightMetricsRuntime` | `FlightMetricsAirspeedResolver` | isolate branch-heavy policy and diagnostics | wind-policy and external-airspeed tests |

### 3.2B Bypass removal plan

| Bypass callsite | Current bypass | Planned replacement | Phase |
|---|---|---|---|
| none | none | no bypass work in this refactor | n/a |

### 3.3 Time base

| Value | Time base | Why |
|---|---|---|
| `currentTimeMillis` | Monotonic in live / replay simulation clock in replay | validity windows and elapsed-state math |
| `wallTimeMillis` | Wall | QNH calibration age only |
| `gpsTimestampMillis` | Sensor/replay sample clock | sample ordering and helper updates |
| `deltaTimeSeconds` | Derived elapsed step in the current execution mode | smoothing, TE, warm-state updates |
| `baroResult.lastCalibrationTime` | Wall | compared only with `wallTimeMillis` |

Explicitly forbidden:

- new wall-clock calls in the runtime split helpers
- replay clock vs wall-time comparisons
- monotonic vs wall comparisons outside the existing calibration-age rule

### 3.4 Threading and cadence

- Dispatcher ownership:
  - unchanged
  - the runtime remains in the existing flight-calculator engine path
- Cadence:
  - unchanged
  - the split must not change the current sensor/update cadence rules
- Hot-path budget:
  - helpers must be long-lived collaborators
  - no per-call helper allocation
  - no new synchronization beyond the existing runtime/use-case guard

### 3.5 Replay determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - unchanged
  - replay finish-ramp behavior and replay vario selection must remain identical

## 4) Data flow (before -> after)

### Before

```text
FlightMetricsRequest
  -> CalculateFlightMetricsRuntime.execute(...)
    -> inline airspeed-resolution policy + counters
    -> SensorFrontEnd
    -> FlightCalculationHelpers / FusionBlackboard
    -> inline display smoothing / needle / ground-zero logic
    -> existing glide calculators
    -> inline FlightMetricsResult assembly
```

### After

```text
FlightMetricsRequest
  -> CalculateFlightMetricsRuntime.execute(...)
    -> FlightMetricsAirspeedResolver
    -> SensorFrontEnd
    -> FlightCalculationHelpers / FusionBlackboard
    -> FlightMetricsDisplayRuntime
    -> existing glide calculators
    -> inline FlightMetricsResult assembly
```

Notes:

- `CalculateFlightMetricsRuntime` remains the single orchestration entrypoint
- this is a same-package split, not a module split
- `FlightMetricsResult` assembly stays inline unless later evidence proves it is still needed for line-budget compliance

## 5) Implementation phases

### Phase 0 - Baseline lock on the actual remaining gaps

- Goal:
  - close the specific coverage gaps that still matter for the planned seams
  - do it without adding noticeable compile-cost or test-harness churn
- Files to change:
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/DisplayVarioSmootherTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/NeedleVarioDynamicsTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTestRuntime.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCaseExternalAirspeedTestRuntime.kt`
  - optionally a new display-focused runtime test file in the same package if the use-case file becomes noisy
- Must add:
  - `DisplayVarioSmootherTest.kt` coverage for `smoothNetto(...)`
  - `DisplayVarioSmootherTest.kt` coverage for `reset()` clearing both vario and netto state
  - `NeedleVarioDynamicsTest.kt` coverage for `reset()`
  - ground-zero settle test for non-flying / low-groundspeed display behavior
  - use-case-level reset test proving the primary display smoother state is cleared, not only the baseline smoother
  - use-case-level reset test proving both needle states are cleared
  - external-airspeed `timestampMillis` freshness fallback test
  - invalid external sample rejection test
  - indicated-airspeed fallback-to-true-speed test
- Explicitly not required in Phase 0 because already covered:
  - `FlightDataManagerSupportTest.kt`
  - `ReplayFinishRampTest.kt`
  - exact wind transition / reset counters
  - warm-state reset parity for Levo / STF / Auto-MC
- Compile-speed guardrails:
  - keep all new tests under existing `src/test` JVM test sources only
  - no instrumentation tests
  - no Robolectric
  - no new Gradle dependencies or plugins
  - prefer extending existing test files over creating broad new fixtures
  - use existing helpers in `CalculateFlightMetricsUseCaseTestSupportRuntime.kt`
  - do not add test-only abstractions unless they are reused at least twice
  - avoid large new parameterized matrices; add only the minimum deterministic cases needed to lock the seam
- Exit criteria:
  - the remaining seam-sensitive behavior is locked
  - no Phase 1 split happens before these tests are in place

### Phase 1 - Extract `FlightMetricsDisplayRuntime`

- Goal:
  - move the stateful display behavior out of the main runtime
  - do it as a true ownership move, not a forwarding wrapper around runtime-owned state
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FlightMetricsDisplayRuntime.kt`
- Files explicitly not expected to change in Phase 1:
  - `CalculateFlightMetricsUseCase.kt`
  - `FlightMetricsRequest.kt`
  - `FlightMetricsResult.kt`
  - `FlightDisplayMapper.kt`
  - `MapScreenUtils.kt`
  - `FlightDataManager.kt`
  - `FlightDataManagerSupport.kt`
  - downstream card/UI files
- Move:
  - shared display smoother for `displayVario` and `displayNetto`
  - baseline smoother
  - slow and fast needle dynamics
  - ground-zero accumulation and reset
  - display output computation helpers
- API guardrails:
  - `FlightMetricsDisplayRuntime` stays same-package `internal`
  - constructor ownership is local:
    - one helper instance is created once in `CalculateFlightMetricsRuntime`
    - the helper owns its smoother and needle collaborators directly
    - runtime must not retain parallel references to those collaborators after extraction
  - do not pass `FlightMetricsRequest`, `GPSData`, `SensorSnapshot`, or `FusionBlackboard.AverageOutputs` wholesale into the new helper
  - pass scalar inputs only:
    - `bruttoVario`
    - `varioValid`
    - `baselineVario`
    - `baselineVarioValid`
    - `rawDisplayNetto`
    - `nettoValid`
    - `deltaTimeSeconds`
    - `isFlying`
    - `groundSpeedMs`
  - return one small internal output object containing:
    - `displayVario`
    - `displayBaselineVario`
    - `displayNetto`
    - `displayNeedleVario`
    - `displayNeedleVarioFast`
  - keep that output object nested in `FlightMetricsDisplayRuntime.kt`; do not add a second standalone models file for this phase
  - preferred helper surface for this phase:
    - `update(...) : DisplayOutputs`
    - `reset()`
  - do not add:
    - helper-level public properties
    - extra statistics accessors
    - factory objects
    - DI surface changes
  - do not let the helper reach into `FusionBlackboard`, `FlightCalculationHelpers`, or `SensorFrontEnd`
- Keep in runtime:
  - the callsite ordering
  - the inputs coming from `FusionBlackboard` and `SensorFrontEnd`
  - `prevTeSpeed`
  - the helper invocation remains inside the existing synchronized runtime path
- Remove from runtime in Phase 1:
  - direct imports of `DisplayVarioSmoother`
  - direct imports of `NeedleVarioDynamics`
  - `kotlin.math.abs` if it becomes display-helper-only
  - display-specific wrapper methods:
    - `smoothDisplayVario(...)`
    - `smoothBaselineDisplayVario(...)`
    - `smoothDisplayNetto(...)`
    - `applyGroundZeroBias(...)`
  - display-specific private constants if they are no longer used elsewhere:
    - `GROUND_ZERO_THRESHOLD_MS`
    - `GROUND_ZERO_SPEED_MS`
    - `GROUND_ZERO_SETTLE_SECONDS`
- Tests to add/update:
  - add a dedicated `FlightMetricsDisplayRuntimeTest.kt`
  - keep existing use-case-level reset/ground-zero tests as regression coverage
  - the new direct helper tests must cover:
    - operation-order-sensitive ground-zero behavior
    - primary/baseline/netto channel independence
    - needle outputs tracking `bruttoVario`
    - helper `reset()`
    - invalid-input decay through the composed helper surface only where the composition adds value beyond the existing low-level smoother/needle tests
  - test-style guardrails:
    - use the real helper with deterministic scalar sequences
    - no mocks in `FlightMetricsDisplayRuntimeTest.kt`
    - no new test-support harness unless reused outside this helper
    - keep use-case-level tests as parity checks, not as the primary place to validate helper internals
  - keep the new direct helper tests in one file; do not introduce new test-support infrastructure unless it is reused beyond this helper
  - existing downstream display tests remain green
- Exit criteria:
  - output parity preserved
  - runtime line count drops materially
  - no duplicate display-state owners introduced
  - runtime reset path delegates display-state clearing only through the helper
  - implemented result: Phase 1 reached the final line target by itself

### Phase 2 - Extract `FlightMetricsAirspeedResolver`

- Goal:
  - move branch-heavy airspeed source logic and diagnostics out of the main runtime
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FlightMetricsAirspeedResolver.kt`
  - optional tiny same-package model file only if necessary
- Move:
  - external-airspeed freshness checks
  - GPS fallback logic
  - altitude selection for wind-estimator input
  - `WindAirspeedEligibilityPolicy`
  - `AirspeedSourceStabilityController`
  - decision and transition counters
  - reset and counter accessors
- Keep in runtime:
  - TE gating with `prevTeSpeed`
  - downstream use of the resolved chosen airspeed
- Tests to add/update:
  - existing wind-policy and external-airspeed tests
  - exact counter / reset parity through the use-case surface
- Exit criteria:
  - chosen-source behavior is identical
  - counter behavior is identical
  - runtime is at or under target, or close enough that only local cleanup remains

### Phase 3 - Local cleanup only if needed

- Goal:
  - finish line-budget compliance without inventing another helper class unless the code still demands it
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
- Allowed:
  - private `buildResult(...)` helper inside the same file if the constructor block still dominates readability
  - small local grouping of intermediate values
  - import cleanup and tiny repeated-expression cleanup
- Explicitly not planned by default:
  - no `FlightMetricsRuntimeState.kt`
  - no top-level `FlightMetricsResultAssembler.kt`
- Exit criteria:
  - `CalculateFlightMetricsRuntime.kt <= 400` lines
  - no new low-value helper class added just for line count

### Phase 4 - Hardening and closeout

- Goal:
  - finish with a release-grade, behavior-stable split
- Files to change:
  - touched runtime/helper files
  - docs only if the effective pipeline description changes
- Verification:
  - rerun required checks
  - confirm no behavior drift in replay/downstream semantics
- Exit criteria:
  - all phase gates satisfied
  - required checks pass, or any unrelated existing blocker is explicitly called out with evidence

## 6) File plan

Planned file set:

- Keep:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsRuntime.kt`
- Add:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FlightMetricsDisplayRuntime.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/FlightMetricsAirspeedResolver.kt`
- Not planned by default:
  - `FlightMetricsRuntimeState.kt`
  - `FlightMetricsResultAssembler.kt`

Rules:

- same package only
- `internal` visibility unless current call surfaces require otherwise
- no new module or DI surface
- no new public API unless unavoidable

## 7) Test plan

- Unit tests:
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/DisplayVarioSmootherTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/NeedleVarioDynamicsTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTestRuntime.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCaseExternalAirspeedTestRuntime.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/CalculateFlightMetricsUseCaseWindPolicyTestRuntime.kt`
  - existing low-level tests:
    - `AirspeedSourceStabilityControllerTest.kt`
    - `WindAirspeedEligibilityPolicyTest.kt`
    - `SensorFrontEndTest.kt`
- Downstream / regression tests:
  - `feature/map/src/test/java/com/trust3/xcpro/flightdata/FlightDisplayMapperTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ConvertToRealTimeFlightDataTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/FlightDataManagerSupportTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/replay/ReplayFinishRampTest.kt`
- Nice-to-have only if a regression appears:
  - a focused `FlightDataEmitter` test for replay-vario/audio snapshot propagation
- Compile-speed policy:
  - these Phase 0 additions should have negligible impact on `assembleDebug` / `compileDebugKotlin` because they stay in JVM test sources
  - the only acceptable cost increase is a small increase in `testDebugUnitTest` runtime from a handful of deterministic unit tests
  - if a proposed test needs Robolectric, Android framework wiring, or new test dependencies, it is out of scope for this phase

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 8) Risks and mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| display-vario or display-netto semantic drift | High | Phase 0 adds direct display-runtime characterization before extraction | Codex |
| airspeed counter drift | High | preserve exact use-case accessor behavior and exact-sequence tests | Codex |
| duplicate smoother/controller state | High | move stateful collaborators whole into the new owner helpers | Codex |
| over-splitting for line count | Medium | no dedicated state-holder or assembler phase by default | Codex |
| replay behavior drift | Medium | keep replay-specific tests green and preserve timebase rules | Codex |

## 9) Acceptance gates

- No architecture violations
- No duplicate SSOT ownership introduced
- Time-base handling remains explicit
- Replay behavior remains deterministic
- `CalculateFlightMetricsRuntime.kt <= 400` lines
- Only the two justified helper extractions are introduced by default
- No final-glide, task, or unrelated UI work enters this refactor
- Required checks pass, or only pre-existing unrelated failures remain and are reported with evidence

## 10) Recommendation

- Proceed in this order:
  1. Phase 0
  2. Phase 1
  3. Phase 2 only if the airspeed seam still buys enough review clarity to justify another split
  4. Phase 3 only if still needed
  5. Phase 4 closeout
- The deep-pass correction is important:
  - the real split is `display runtime` plus `airspeed resolver`
  - do not add a dedicated state-holder file
  - do not add a top-level result assembler unless evidence says the runtime is still oversized after the two real seams
