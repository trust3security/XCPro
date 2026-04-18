# CHANGE_PLAN_AGL_PILOT_SAFE_RUNTIME_2026-02-27.md

## 0) Metadata

- Title: Pilot-Safe AGL Runtime Optimization (Keep Functionality, Reduce Cost)
- Owner: XCPro sensors/domain + map/runtime + test infra
- Date: 2026-02-27
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  - AGL is useful to pilots (terrain clearance awareness, AGL card visibility, and flying-state override), but current live-path update frequency can create avoidable CPU/lock/allocation pressure.
  - Current flight-state path consumes raw finite AGL with no explicit freshness contract, so stale AGL can influence `isFlying`.
- Why now:
  - AGL is in a hot path and runs during live flight.
  - Pilot-facing behavior must stay correct while reducing resource pressure.
- In scope:
  - Preserve AGL functionality; do not remove AGL from runtime outputs.
  - Add explicit AGL freshness contract for downstream consumers.
  - Reduce avoidable AGL churn with adaptive scheduling/gating in live mode.
  - Adopt a release contract of `20s` base AGL cadence with immediate-trigger exceptions.
  - Add pilot-safety regression tests and burst/perf evidence.
- Out of scope:
  - TE/brutto/netto algorithm changes unrelated to AGL.
  - Replay model changes (replay already disables online terrain lookup).
  - UI redesign of vario widgets/cards.
- User-visible impact:
  - AGL remains available.
  - Better stability under bursty updates and lower runtime overhead.
  - No intended change to core climb/sink audio logic.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Current AGL value | `FlightCalculationHelpers` -> `CompleteFlightData` | `CompleteFlightData.agl` | Ad-hoc parallel AGL caches in UI/ViewModel |
| AGL freshness metadata | `FlightCalculationHelpers` (worker output metadata) | domain-safe freshness field consumed by repository/domain | Deriving freshness in multiple layers with different clocks |
| Flying-state decision | `FlightStateRepository` + `FlyingStateDetector` | `flightState` `StateFlow` | Independent flying-state logic in UI/map runtime |

### 2.2 Dependency Direction

Confirmed unchanged:

`UI -> domain -> data`

- Modules/files touched (planned):
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/*`
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/domain/*`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/*`
  - `feature/map/src/test/java/com/trust3/xcpro/sensors/domain/*`
  - `docs/ARCHITECTURE/*` evidence/plan updates
- Boundary risk:
  - Medium: flight-state gating behavior can change if freshness policy is wrong.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| AGL freshness interpretation | Implicit (any finite value treated as usable) | Explicit domain contract in sensors/domain path | Prevent stale AGL from affecting flying-state | Unit tests for stale/high AGL behavior |
| AGL update cadence control | Per-emit callsite pressure | Centralized adaptive gate around AGL updates | Reduce hot-path churn while preserving latest-wins | Burst tests + perf evidence |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Flight-state uses finite AGL without age gate | Any finite AGL can trigger override | Use only fresh AGL (monotonic age bound) for override | Phase 2 |
| Unconditional live `updateAGL` call pressure | Called on each eligible metrics emit | Adaptive submission gate (time/distance/flight-context aware) | Phase 1 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| AGL sample freshness age | Monotonic | Stable aging unaffected by wall-clock jumps |
| Replay flight timestamps | Replay | Determinism for replay outputs |
| UI display timestamps | Wall (live) / Replay (replay) | Existing output contract |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - AGL computation worker remains background coroutine owned by calculator scope.
  - No blocking calls on Main.
- Primary cadence/gating sensor:
  - Live GPS/baro emission path (`updateAGL` submission path only).
- Hot-path latency budget:
  - Keep AGL submission non-blocking.
  - Reduce worker churn without removing latest-wins behavior.
- AGL cadence contract (live mode):
  - Base submission interval: `20_000 ms`.
  - Immediate update triggers (bypass interval gate):
    - Horizontal movement >= `200 m` since last submitted AGL request.
    - Altitude change >= `25 m` since last submitted AGL request.
    - Flight-state transition boundary events (takeoff/landing candidate).
  - AGL freshness contract for consumers:
    - Treat AGL as stale after `15_000 ms` if no successful update arrives.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules:
  - Replay keeps online terrain lookup disabled.
  - AGL optimization targets live mode only unless explicitly safe for replay.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Stale AGL incorrectly drives `isFlying` | ARCHITECTURE timebase + SSOT | unit tests + review | `FlyingStateDetector` / `FlightStateRepository` tests |
| AGL optimization drops required functionality | ARCHITECTURE SSOT/UDF | regression tests | `FlightCalculationHelpersTest`, mapping tests |
| Hot-path regression persists | CONTRIBUTING perf budget | burst/perf tests + evidence artifact | AGL stress tests + docs evidence |
| Replay behavior accidentally changed | ARCHITECTURE replay determinism | replay gate tests | `CalculateFlightMetricsUseCaseTest` replay case |

## 3) Data Flow (Before -> After)

Before:

`GPS/Baro emit -> metrics execute -> updateAGL attempt -> AGL worker -> currentAGL -> CompleteFlightData -> FlightStateRepository uses finite AGL`

After:

`GPS/Baro emit -> adaptive AGL submission gate -> AGL worker -> currentAGL + freshness metadata -> CompleteFlightData -> FlightStateRepository uses fresh AGL only`

## 4) Implementation Phases

### Phase 0 - Baseline and Pilot Contract

- Goal:
  - Lock pilot-facing invariants before code changes.
- Files to change:
  - This plan doc and optional evidence notes under `docs/ARCHITECTURE/evidence/`.
- Tests to add/update:
  - None in this phase.
- Exit criteria:
  - Invariants written and agreed:
    - AGL remains available in live outputs/cards.
    - Core climb/sink audio source logic unchanged.
    - Flying-state AGL override uses fresh data only.
    - Live cadence contract fixed:
      - `20_000 ms` base cadence,
      - `200 m` horizontal / `25 m` altitude trigger overrides,
      - `15_000 ms` freshness stale threshold.

### Phase 1 - Adaptive AGL Submission (Performance)

- Goal:
  - Reduce avoidable AGL worker churn while preserving latest-wins.
- Files to change:
  - `FlightCalculationHelpers.kt` and/or `CalculateFlightMetricsUseCase.kt` AGL submission path.
- Tests to add/update:
  - Burst/coalescing tests (existing suite extended) for:
    - latest update wins,
    - worker remains single-owner,
    - no recursive relaunch regression.
  - New cadence tests:
    - no-trigger path honors >= `20_000 ms` submission spacing,
    - movement trigger bypasses gate at `>=200 m`,
    - altitude trigger bypasses gate at `>=25 m`.
- Exit criteria:
  - AGL still updates correctly.
  - Stress test proves bounded worker behavior under high-frequency submissions.
  - Cadence/trigger tests pass.

### Phase 2 - Freshness-Safe Flying-State Integration (Pilot Safety)

- Goal:
  - Prevent stale AGL from forcing flying-state decisions.
- Files to change:
  - `FlightStateRepository.kt`
  - `FlyingStateDetector.kt` (if needed for explicit contract)
  - related model/mapping files for freshness metadata exposure
- Tests to add/update:
  - New tests:
    - stale high AGL does not keep `isFlying` true,
    - fresh high AGL still supports intended override behavior,
    - no regressions for normal speed-based takeoff/landing.
    - stale threshold behavior at `15_000 ms` boundary.
- Exit criteria:
  - Flying-state behavior is deterministic and freshness-safe.
  - Flight-state only uses fresh AGL per contract.

### Phase 3 - Telemetry + Evidence + Docs

- Goal:
  - Produce release-auditable evidence and operational visibility.
- Files to change:
  - AGL metrics/counters exposure (processed/dropped/error/freshness state).
  - `docs/ARCHITECTURE/evidence/...` report.
  - `PIPELINE.md` only if wiring/ownership changed.
- Tests to add/update:
  - Counter contract tests where practical.
  - Perf evidence harness command and reproducibility note.
- Exit criteria:
  - Before/after evidence committed (timing + allocations + run metadata).
  - Counters available for runtime verification.

### Phase 4 - Release Gate + Rollback Readiness

- Goal:
  - Confirm release readiness and rollback path.
- Files to change:
  - Plan status + PR notes checklist sections.
- Tests to add/update:
  - None new unless verification reveals gaps.
- Exit criteria:
  - Full release gate passes (or documented blocker with owner/expiry).
  - One-step rollback note included with validation commands.

## 5) Test Plan

- Unit tests:
  - `FlightCalculationHelpersTest` burst/coalescing/exception recovery.
  - `FlyingStateDetectorTest` + repository tests for fresh vs stale AGL behavior.
  - Mapping tests for AGL/freshness propagation as needed.
  - Cadence-contract tests for `20_000 ms` base and movement/altitude trigger bypass.
- Replay/regression tests:
  - Keep replay terrain lookup disabled behavior unchanged.
- UI/instrumentation tests (if needed):
  - Not required unless AGL display wiring changes.
- Degraded/failure-mode tests:
  - terrain fetch failures, stale AGL, and worker exception resilience.
- Boundary tests for removed bypasses:
  - stale AGL cannot bypass speed/timer logic in flying-state.
  - AGL stale boundary (`15_000 ms`) does not regress takeoff/landing transitions.

### 5.1 Fast Minimum Local Gate

```bash
./gradlew enforceRules
test-safe.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.sensors.FlightCalculationHelpersTest"
test-safe.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.sensors.domain.FlyingStateDetectorTest"
./gradlew :feature:map:compileDebugKotlin
```

### 5.2 Full Release Gate (before merge)

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when environment is available:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Over-throttled AGL updates reduce usefulness near terrain | High | Adaptive policy with safety floor + pilot-safety tests | sensors/domain |
| Freshness threshold too strict/loose | Medium | Start conservative, validate with flight logs and tests | sensors/domain |
| Runtime counters add overhead | Low | Use lightweight atomic counters and sample logging | sensors/runtime |
| Hidden regression in flying-state transitions | High | Dedicated transition tests (takeoff/landing + stale AGL scenarios) | sensors/domain |
| 20s base cadence delays perceived AGL refresh | Medium | Trigger-based immediate updates + 15s freshness guard | sensors/domain |

## 7) Acceptance Gates

- AGL functionality remains present (not removed) in live pipeline output.
- No architecture/rules violations (`ARCHITECTURE.md`, `CODING_RULES.md`).
- Flying-state AGL override is freshness-gated and tested.
- Burst/perf evidence shows reduced churn with no behavior regression.
- Cadence contract implemented and tested:
  - `20_000 ms` base,
  - `200 m`/`25 m` trigger bypass,
  - `15_000 ms` stale threshold.
- Replay behavior remains deterministic and unchanged in intent.
- Full release gate passes or blocker is documented with owner/expiry.

## 8) Rollback Plan

- What can be reverted independently:
  - AGL adaptive scheduling changes.
  - Freshness-gating integration in flight-state.
  - Telemetry/evidence-only additions.
- One-step rollback command:
  - `git revert <merge_or_commit_sha>`
- Post-rollback verification:
  1. `./gradlew enforceRules`
  2. `test-safe.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.sensors.FlightCalculationHelpersTest"`
  3. `./gradlew assembleDebug`
