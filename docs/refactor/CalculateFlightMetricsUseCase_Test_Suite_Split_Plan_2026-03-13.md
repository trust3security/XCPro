# CalculateFlightMetricsUseCase test-suite split plan

## 0) Metadata

- Title: `CalculateFlightMetricsUseCase` runtime test-suite split plan
- Owner: Codex
- Date: 2026-03-13
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  - `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTestRuntime.kt`
    has grown into a single large runtime regression suite with mixed concerns.
  - The file is hard to review, hard to navigate, and makes failure triage slower
    because unrelated behaviors fail in the same suite.
- Why now:
  - the current suite is well above the desired maintenance budget
  - the test concerns are already separable by behavior without changing coverage
- In scope:
  - test-only refactor
  - shared helper extraction
  - concern-based split into focused runtime test files
  - keeping each resulting file under the desired `400`-line target
- Out of scope:
  - no production code changes
  - no behavior changes
  - no pipeline wiring or replay-policy changes
  - no new abstractions outside test support

## 2) Architecture Contract

### 2.1 SSOT Ownership

No production SSOT owners change. This refactor touches JVM tests only.

### 2.2 Dependency Direction

Production dependency direction remains unchanged:

`UI -> domain -> data`

Only test sources under `feature/map/src/test/.../sensors/domain` are changed.

### 2.3 Time Base

Test scenarios must preserve the existing explicit time-base semantics already
locked by the suite:

| Value | Time Base | Why |
|---|---|---|
| `currentTimeMillis` | live monotonic or replay simulation step | validity and runtime windows |
| `wallTimeMillis` | wall | QNH calibration age checks |
| `gpsTimestampMillis` | sample clock | TC30s and gating behavior |

Forbidden:

- changing time semantics while moving tests
- merging wall-time and monotonic scenarios into generic helpers that hide intent

### 2.4 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules: unchanged

## 3) Target Suite Ownership

- `CalculateFlightMetricsUseCaseTc30sTestRuntime.kt`
  - 30-second average tracking, spike rejection, and GPS-tick gating
- `CalculateFlightMetricsUseCaseResetTestRuntime.kt`
  - display-state reset and ground-zero settling
- `CalculateFlightMetricsUseCaseQnhReplayTestRuntime.kt`
  - QNH semantics, replay terrain-lookup suppression, and TE/GPS-only semantics
- `CalculateFlightMetricsUseCaseGlideMetricsTestRuntime.kt`
  - reset parity for Levo netto, speed-to-fly, and auto-MC history

Existing owners that must remain unchanged:

- `CalculateFlightMetricsUseCaseExternalAirspeedTestRuntime.kt`
- `CalculateFlightMetricsUseCaseWindPolicyTestRuntime.kt`

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  - inventory current test ownership and prevent overlap with external-airspeed
    and wind-policy suites
- Exit criteria:
  - every current test has a destination file
  - no assertions are intentionally changed

### Phase 1 - Shared fixture extraction

- Goal:
  - move reusable helpers out of the oversized suite into
    `CalculateFlightMetricsUseCaseTestSupportRuntime.kt`
- Allowed:
  - deterministic request builders
  - shared glide/circling helpers reused by multiple files
- Exit criteria:
  - helpers are centralized
  - helper APIs stay small and test-oriented

### Phase 2 - Concern-based split

- Goal:
  - create the focused suites and move tests without behavior changes
- Exit criteria:
  - each resulting test file stays under `400` lines
  - test names and assertions keep the same semantic meaning

### Phase 3 - Cleanup

- Goal:
  - update or retain the facade file only if it still adds value
  - remove dead comments or misleading pointers
- Exit criteria:
  - no stale pointer comments remain
  - suite names reflect actual ownership

### Phase 4 - Verification

- Goal:
  - confirm the split did not change behavior or introduce new rule failures
- Required checks:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## 5) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| suite overlap with external-airspeed or wind-policy tests | Medium | lock ownership before moving tests | Codex |
| helper over-abstraction hides scenario intent | Medium | keep helpers scalar and narrow | Codex |
| churn from unnecessary rename/delete work | Medium | change only the oversized suite and support files | Codex |
| behavioral drift while moving assertions | High | move tests verbatim first, then do only minimal cleanup | Codex |

## 6) Acceptance Gates

- no production files change
- no test assertion behavior changes
- clearer ownership and failure triage than the original single file
- each new suite stays under `400` lines
- required verification passes, or only unrelated pre-existing failures remain and
  are reported explicitly
