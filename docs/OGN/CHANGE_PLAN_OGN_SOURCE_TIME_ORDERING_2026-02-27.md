# CHANGE_PLAN_OGN_SOURCE_TIME_ORDERING_2026-02-27.md

## Purpose

Define a phased, architecture-safe implementation plan to eliminate OGN marker
"forward/backward/forward" oscillation caused by out-of-order APRS delivery.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN source-time ordering and anti-rewind stabilization
- Owner: XCPro Team
- Date: 2026-02-27
- Issue/PR: TBD
- Status: Implemented (Phase 1/2/3/4 core scope)
- Follow-on hardening plan:
  - `docs/OGN/CHANGE_PLAN_OGN_CONNECTIVITY_RELIABILITY_2026-03-01.md`
  - Covers untimed-frame ordering, inbound-liveness authority, and connected-session DDB refresh cadence (implemented 2026-03-01).

## 0A) Implementation Update (2026-02-27)

Implemented now:
- Added APRS source-time extraction in parser for:
  - `/hhmmssh` (UTC hour/minute/second)
  - `@ddhhmmz` (UTC day/hour/minute)
- Added `sourceTimestampWallMs` to `OgnTrafficTarget`.
- Added repository source-time anti-rewind gate:
  - out-of-order source-time frames are dropped (`rewindToleranceMs=0`).
- Added repository motion plausibility gate:
  - implausible teleport-like jumps are dropped from position apply path.
- Added diagnostics counters in `OgnTrafficSnapshot`:
  - `droppedOutOfOrderSourceFrames`
  - `droppedImplausibleMotionFrames`
- Added/updated tests:
  - parser source-time parse and invalid-token fallback
  - source-time anti-rewind policy helper tests
  - motion plausibility policy helper tests

Current intentional simplification:
- No per-target reorder buffer is enabled in this change.
- Policy is strict anti-rewind drop + plausibility validation.

## 1) Scope

- Problem statement:
  - OGN traffic rendering currently applies latest-arrived sample order, not
    source event time order. Delayed frames can overwrite newer positions and
    create visible marker rewind/jump behavior.
- Why now:
  - Marker stability directly affects pilot trust and readability of OGN traffic.
  - Existing track stabilization only smooths heading; it does not protect
    position updates from out-of-order arrival.
- In scope:
  - APRS source timestamp extraction (`h` and `z` timestamp forms used by OGN/APRS).
  - Repository event-time ordering policy with bounded per-target reorder window.
  - Anti-rewind guard and motion plausibility gate for position updates.
  - Diagnostics in OGN snapshot/debug surface for dropped/reordered frames.
  - Tests + docs synchronization.
- Out of scope:
  - OGN uplink/transmit.
  - Collision avoidance or tactical alert logic.
  - ADS-B data path changes.
- User-visible impact:
  - Reduced marker ping-pong in sparse or unstable network conditions.
  - More stable OGN trails/thermals because they consume ordered/fresh targets.

## 1A) Advisory ("Genius-grade") Strategy

1. Use event time as truth:
   - Order by source packet time when present, not socket arrival order.
2. Keep a short reorder window:
   - Buffer a small per-target window (for example 1000-2000 ms) to recover
     natural out-of-order delivery without adding visible lag.
3. Reject rewinds with tolerance:
   - If a sample is older than the last committed source time by more than
     tolerance, drop it and record diagnostic counters.
4. Add kinematic sanity:
   - Drop impossible jumps (distance/time implies implausible speed) before UI.
5. Keep rendering visual-only:
   - Do not move ordering/safety policy into UI overlays.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Parsed APRS source timestamp (`sourceWallMs`) | `OgnAprsLineParser` -> `OgnTrafficRepository` | field in `OgnTrafficTarget` | UI-local timestamp parsing |
| Ingest arrival monotonic time (`ingestMonoMs`) | `OgnTrafficRepository` | field in `OgnTrafficTarget` or internal state | Overlay/runtime arrival-time mirrors |
| Per-target reorder state | `OgnTrafficRepository` | internal map/buffer | ViewModel/overlay reorder buffers |
| Last accepted source time per target | `OgnTrafficRepository` | internal policy state | trail/thermal independent reorder logic |
| Drop/reorder diagnostics | `OgnTrafficRepository` | `OgnTrafficSnapshot` diagnostics fields | ad-hoc debug counters in UI |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnAprsLineParser.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnGliderTrailRepository.kt` (freshness compatibility checks)
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt` (freshness compatibility checks)
- Boundary risk:
  - Low if all ordering rules stay repository-side.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Effective sample ordering policy | implicit arrival order in `OgnTrafficRepository` | explicit source-time ordering policy in `OgnTrafficRepository` | prevent rewind jitter | repository policy tests |
| Timestamp extraction semantics | parser ignores APRS source timestamp for ordering | parser extracts timestamp candidate and passes upstream | make event-time available | parser tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `OgnTrafficRepository.handleIncomingLine` | direct apply in arrival order | per-target ordered apply with reorder window + anti-rewind gate | Phase 2 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| `sourceWallMs` (from APRS packet timestamp) | Wall | event-time ordering anchor from packet payload |
| `ingestMonoMs` | Monotonic | deterministic local elapsed timing for windows and timeouts |
| stale/eviction timers | Monotonic | current OGN lifecycle policy already monotonic |
| reorder window duration | Monotonic | robust elapsed-time math independent of wall clock shifts |
| debug "when happened" labels | Wall | human-readable diagnostics only |

Explicitly forbidden comparisons:

- Monotonic vs wall direct subtraction/comparison.
- Replay vs wall direct subtraction/comparison.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Parser/repository logic on injected OGN IO/default repository scope.
  - UI overlay cadence remains in `MapOverlayManager`.
- Primary cadence/gating sensor:
  - OGN socket line ingress cadence.
- Hot-path latency budget:
  - Reorder + validation policy target < 1 ms per target update on average.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (policy is pure and threshold-driven).
- Randomness used: No.
- Replay/live divergence rules:
  - Replay unaffected (OGN network path is live-only).

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Policy leaks to UI | ARCHITECTURE SSOT/UDF | review + unit tests | `OgnTrafficRepositoryPolicyTest` |
| Time-base misuse (mono vs wall mix) | ARCHITECTURE Timebase | unit tests | new repository timebase tests |
| Trail/thermal freshness regression | PIPELINE OGN lifecycle semantics | unit tests | `OgnGliderTrailRepositoryTest`, `OgnThermalRepositoryTest` |
| Parser timestamp mis-parse | OGN protocol notes | unit tests | `OgnAprsLineParserTest` |

## 3) Data Flow (Before -> After)

Before:

`Socket line -> parse target -> merge identity -> publish/overwrite by arrival order -> render`

After:

`Socket line -> parse target + source time candidate -> anti-rewind + plausibility gate -> publish ordered target -> render`

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  - Capture and lock current behavior with tests that reproduce rewind risk.
- Files to change:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
- Tests to add/update:
  - Out-of-order arrival sequence test (newer packet arrives before older).
  - Assert current behavior (for baseline), then update expected in Phase 2.
- Exit criteria:
  - Baseline tests committed and reproducible.
  - Status: Partial (policy helper coverage added; explicit connection-sequence baseline test deferred).

### Phase 1 - Parser/model event-time extraction

- Goal:
  - Parse APRS timestamp tokens and expose source event-time candidate in model.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnAprsLineParser.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnAprsLineParserTest.kt`
- Tests to add/update:
  - Parse `/hhmmssh` timestamp form.
  - Parse `@ddhhmmz` timestamp form (if supported).
  - Invalid timestamp fallback behavior.
- Exit criteria:
  - Parser exposes valid source timestamp candidate and does not regress existing fields.
  - Status: Complete.

### Phase 2 - Repository source-time ordering and anti-rewind

- Goal:
  - Replace arrival-order overwrite with event-time ordered apply policy.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
- Tests to add/update:
  - Out-of-order packet delivery no longer rewinds committed position.
  - Small reorder-window late packet can still be accepted in-order.
  - Anti-rewind tolerance drop behavior and diagnostics counters.
- Exit criteria:
  - Marker position monotonic by source-time policy for same target.
  - Status: Core complete (strict anti-rewind drop policy; reorder window deferred).

### Phase 3 - Motion plausibility hardening

- Goal:
  - Reject impossible teleport-like updates and avoid oscillation from bad packets.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
- Tests to add/update:
  - Impossible speed jump rejection by time delta.
  - Valid high-speed tug scenarios remain accepted.
- Exit criteria:
  - No false rewinds from implausible outliers under test vectors.
  - Status: Core complete.

### Phase 4 - Downstream compatibility and diagnostics

- Goal:
  - Ensure thermal/trail repositories consume ordered samples without regression.
  - Expose drop/reorder counters in snapshot for debug panel.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnGliderTrailRepository.kt` (if needed)
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalRepository.kt` (if needed)
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnGliderTrailRepositoryTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnThermalRepositoryTest.kt`
- Tests to add/update:
  - Freshness monotonic assumptions still hold with new timestamp fields.
  - Diagnostics counters update as expected.
- Exit criteria:
  - Trails/thermals remain deterministic and clean under reordered input.
  - Status: Complete for diagnostics model fields and compatibility verification coverage.

### Phase 5 - Docs and rollout

- Goal:
  - Synchronize architecture and OGN runtime docs with implemented policy.
- Files to change:
  - `docs/OGN/OGN.md`
  - `docs/OGN/OGN_PROTOCOL_NOTES.md`
  - `docs/ARCHITECTURE/PIPELINE.md` (if runtime wiring signatures changed)
- Tests to add/update:
  - N/A (docs), but include command evidence in PR.
- Exit criteria:
  - Docs accurately reflect source-time ordering behavior and diagnostics.
  - Status: Complete for OGN docs in this folder.

## 5) Test Plan

- Unit tests:
  - `OgnAprsLineParserTest`
  - `OgnTrafficRepositoryPolicyTest`
  - `OgnTrafficRepositoryConnectionTest`
  - `OgnGliderTrailRepositoryTest` (freshness compatibility)
  - `OgnThermalRepositoryTest` (freshness compatibility)
- Replay/regression tests:
  - Not applicable to live OGN ingest path; replay remains unchanged.
- UI/instrumentation tests (if needed):
  - Optional debug-panel diagnostics visibility checks.
- Degraded/failure-mode tests:
  - malformed timestamp
  - late packet beyond reorder window
  - implausible teleport packet
- Boundary tests for removed bypasses:
  - ensure arrival-order overwrite path is replaced by ordered apply policy.

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

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Overly strict anti-rewind drops valid updates | Medium | tune tolerance + add real-world APRS vectors | XCPro Team |
| Reorder window adds perceived latency | Medium | keep window short and bounded per target | XCPro Team |
| Timebase confusion in code | High | explicit `sourceWallMs` vs `ingestMonoMs` naming + tests | XCPro Team |
| Trail/thermal behavior drift | Medium | compatibility tests on fresh-sample logic | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling explicit in code/tests (`sourceWallMs`, `ingestMonoMs`).
- OGN marker rewind regression fixed in out-of-order tests.
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved.

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 3 plausibility gate can be toggled off while retaining Phase 2 ordering.
  - Phase 2 ordering can be reverted to current arrival-order behavior if severe field regressions occur.
- Recovery steps if regression is detected:
  1. Disable/rollback plausibility gate first.
  2. Keep parser/model fields (non-breaking) while reverting apply policy.
  3. Re-run OGN repository test suite and restore in smaller patch.

## 9) Verification Evidence (2026-02-27)

Executed successfully:

```bash
./gradlew.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.ogn.OgnAprsLineParserTest" --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryPolicyTest"
./gradlew.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryConnectionTest" --tests "com.trust3.xcpro.ogn.OgnGliderTrailRepositoryTest" --tests "com.trust3.xcpro.ogn.OgnThermalRepositoryTest"
./gradlew.bat :feature:map:assembleDebug
./gradlew.bat :app:assembleDebug
```
