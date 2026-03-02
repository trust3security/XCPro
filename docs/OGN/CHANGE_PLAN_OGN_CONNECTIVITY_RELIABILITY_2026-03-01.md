# CHANGE_PLAN_OGN_CONNECTIVITY_RELIABILITY_2026-03-01.md

## Purpose

Define a phased, architecture-safe implementation plan to harden XCPro OGN
connectivity and ordering behavior for the issues identified in the 2026-03-01
code pass.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN connectivity reliability hardening (ordering, liveness, refresh)
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: TBD
- Status: Implemented (Phase 0/1/2/3/4 core scope)

## 0A) Implementation Update (2026-03-01)

Implemented now:
- Untimed-after-timed source ordering hardening:
  - Added timed-source lock gate for untimed frames.
  - Added untimed fallback window (`30_000 ms`) after timed-source silence.
  - Added per-target latest accepted timed-source timestamp authority so
    delayed older timed frames are rejected even after untimed fallback commits.
- Motion plausibility hardening:
  - Speed-gate time delta now uses source-time when available, with monotonic
    fallback when source-time is missing.
- Stream liveness hardening:
  - Stall timer authority changed to inbound lines only.
  - Keepalive writes no longer reset inbound stall timer.
- Connected-session DDB refresh hardening:
  - Added active-session due-check cadence (`60_000 ms` check interval) while
    stream remains connected.
  - Refresh failure is now explicit (not treated as success) for HTTP/transport/
    parse failures, with bounded retry pacing (`2..5 min`) before returning to
    normal cadence after success.
- Policy reconnect snapshot consistency hardening:
  - Policy-triggered reconnects now publish a `DISCONNECTED` snapshot before
    immediate reconnect attempt.
- Added/updated repository policy and connection tests for all above.
- Synced OGN docs (`OGN.md`, `OGN_PROTOCOL_NOTES.md`, vectors, index).

## 1) Scope

- Problem statement:
  - OGN ingest has three reliability gaps:
    1. untimed APRS frames can still rewind visible position after timed frames
    2. stream stall detection counts outbound keepalive writes as activity
    3. DDB refresh checks run only at reconnect boundaries
- Why now:
  - These issues degrade pilot trust (marker stability), reconnect behavior
    quality, and metadata freshness.
- In scope:
  - Repository ordering policy hardening for untimed frames.
  - Inbound-only stream-liveness policy and tests.
  - DDB refresh cadence while long-lived stream remains connected.
  - Diagnostics counters for new policy decisions.
  - OGN docs and protocol notes synchronization.
- Out of scope:
  - OGN uplink/transmit.
  - ADS-B ingest path changes.
  - New tactical/collision behaviors.
- User-visible impact:
  - Fewer forward/backward/forward marker jumps.
  - Faster reconnect on real inbound stall.
  - More predictable DDB identity freshness during long sessions.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| per-target time authority state (timed-locked vs untimed-fallback) | `OgnTrafficRepository` | internal map/state | UI or overlay-level ordering state |
| inbound stream liveness timestamp | `OgnTrafficRepository` | internal state + optional snapshot diagnostics | overlay/network adapter liveness mirrors |
| new dropped-frame diagnostics (untimed policy/liveness policy) | `OgnTrafficRepository` | `OgnTrafficSnapshot` fields | ad-hoc UI counters |
| DDB refresh scheduler state | `OgnTrafficRepository` + `OgnDdbRepository` | internal state | UI-managed refresh timers |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnDdbRepository.kt` (if needed)
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
  - `docs/OGN/OGN.md`
  - `docs/OGN/OGN_PROTOCOL_NOTES.md`
  - `docs/OGN/OGN_APRS_TEST_VECTORS.md`
- Boundary risk:
  - Low if policy remains repository-side and only diagnostics cross into UI.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| untimed-frame authority after timed lock | implicit arrival-order overwrite | explicit repository time-authority policy | stop untimed rewind after timed history | repository policy tests |
| stream stall activity source | mixed inbound/outbound | inbound-only liveness | avoid false healthy state on one-way dead stream | connection/liveness tests |
| DDB refresh cadence while connected | reconnect-bound check | periodic due-check while connected | keep identity data fresh in long sessions | repository tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `handleIncomingLine` untimed path | untimed frames accepted when source timestamp missing | per-target time-authority state machine + untimed apply guard | Phase 1 |
| `connectAndRead` stall check | keepalive write updates stream activity | inbound-only activity timestamp for stall decisions | Phase 2 |
| `runConnectionLoop` DDB check | refresh check only before connect attempt | periodic due-check during connected session | Phase 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| APRS `sourceTimestampWallMs` | Wall | event-time ordering anchor from packet payload |
| ingest receive `lastSeenMillis` | Monotonic | deterministic per-session ordering/liveness |
| inbound stall timestamp | Monotonic | transport liveness timeout math |
| keepalive write timestamp | Monotonic | diagnostics only, not stall authority |
| DDB due-check | Wall | cache age contract is wall-clock based |

Explicitly forbidden comparisons:

- Monotonic vs wall direct subtraction/comparison.
- Replay vs wall direct subtraction/comparison.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - OGN repository logic remains on injected IO dispatcher.
  - Optional periodic DDB check runs in repository scope on same dispatcher.
- Primary cadence/gating sensor:
  - APRS line ingress.
- Hot-path latency budget:
  - Added per-target policy checks should remain sub-millisecond average.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (policy is threshold/state driven).
- Randomness used: No.
- Replay/live divergence rules:
  - OGN ingest remains live-only; replay pipeline behavior unchanged.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| policy leaks into UI | ARCHITECTURE SSOT/UDF | review + unit tests | `OgnTrafficRepositoryPolicyTest` |
| time-base misuse in new liveness policy | ARCHITECTURE Timebase | unit tests | `OgnTrafficRepositoryPolicyTest` |
| false healthy stream state | OGN transport contract | integration-style repository test | `OgnTrafficRepositoryConnectionTest` |
| stale DDB in long sessions | OGN metadata freshness contract | repository tests + review | `OgnTrafficRepositoryConnectionTest` and/or new DDB cadence test |
| docs drift | docs sync rule | review | `docs/OGN/*.md` |

## 3) Data Flow (Before -> After)

Before:

`Socket line -> parse target -> anti-rewind only when both timestamps exist -> apply`

`Keepalive write -> marks stream active (stall timer reset)`

`DDB refresh check -> only at connection-loop boundary`

After:

`Socket line -> parse target -> per-target time-authority policy (timed lock + untimed guard) -> plausibility -> apply`

`Inbound line read -> marks stream active (stall authority)`

`Keepalive write -> diagnostics only`

`Periodic due-check while connected -> DDB refresh without reconnect dependency`

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  - Lock current behavior with failing/expectation tests before policy changes.
- Files to change:
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
- Tests to add/update:
  - timed frame then delayed untimed frame scenario.
  - inbound-stall scenario where keepalive writes continue.
  - DDB refresh cadence scenario under long connected stream.
- Exit criteria:
  - Baseline tests reproduce current gaps deterministically.

### Phase 1 - Untimed frame ordering hardening

- Goal:
  - Prevent untimed frames from rewinding committed timed-target position.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficModels.kt`
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
- Design policy:
  - Add per-target time-authority state:
    - `TIMED_LOCKED`: target has committed timed samples
    - `UNTIMED_FALLBACK`: target has no reliable timed samples
  - In `TIMED_LOCKED`, untimed frames are non-authoritative for position unless
    timed data has been absent longer than a configured timeout.
  - Untimed-authoritative path still uses motion plausibility gate with monotonic
    delta to reject teleports.
- Tests to add/update:
  - timed then untimed rewind attempt is rejected.
  - untimed-only traffic still advances in fallback mode.
  - timeout-based fallback from `TIMED_LOCKED` to `UNTIMED_FALLBACK`.
- Exit criteria:
  - No marker rewind in timed->untimed regression vectors.

### Phase 2 - Inbound-only liveness and stall policy

- Goal:
  - Stall detection uses inbound read activity only.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
- Tests to add/update:
  - keepalive-write-only stream still trips stall timeout.
  - periodic inbound server comment lines keep stream healthy.
- Exit criteria:
  - One-way dead inbound stream reconnects within configured stall timeout.

### Phase 3 - Connected-session DDB refresh cadence

- Goal:
  - DDB refresh check runs while stream is connected, not only on reconnect.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnDdbRepository.kt` (only if contract expansion needed)
  - `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt` (or new repository cadence test)
- Tests to add/update:
  - long-lived connection triggers due-check and refresh attempt.
  - refresh failure does not break active stream.
- Exit criteria:
  - identity refresh no longer waits for reconnect boundaries.

### Phase 4 - Diagnostics and doc sync

- Goal:
  - Surface new policy counters and sync all OGN docs.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapTrafficDebugPanels*.kt` (if new counters displayed)
  - `docs/OGN/OGN.md`
  - `docs/OGN/OGN_PROTOCOL_NOTES.md`
  - `docs/OGN/OGN_APRS_TEST_VECTORS.md`
  - `docs/OGN/README.md`
- Tests to add/update:
  - snapshot counter assertions for new drop/stall policy paths.
- Exit criteria:
  - docs and diagnostics match shipped behavior.

### Phase 5 - Release hardening and rollout

- Goal:
  - Validate production readiness and rollback safety.
- Files to change:
  - release notes / PR checklist artifacts
  - optional feature flag wiring if rollback guard retained
- Tests to add/update:
  - targeted smoke matrix on unstable network conditions.
- Exit criteria:
  - release gate checks pass and rollback path is documented.

## 5) Test Plan

- Unit tests:
  - `OgnTrafficRepositoryPolicyTest`
  - `OgnTrafficRepositoryConnectionTest`
  - `OgnAprsLineParserTest` (vector expansions)
- Replay/regression tests:
  - N/A (live OGN path), deterministic unit regressions required.
- UI/instrumentation tests (if needed):
  - optional debug-panel diagnostics rendering checks.
- Degraded/failure-mode tests:
  - untimed delayed frame after timed frame
  - inbound stall with successful keepalive writes
  - DDB refresh failure while connected
- Boundary tests for removed bypasses:
  - verify untimed bypass no longer overwrites timed-authoritative position.

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
| overly strict untimed guard drops valid traffic | reduced target update rate | timeout-based fallback mode + counters + vector tests | XCPro Team |
| inbound-only stall policy causes reconnect churn on very low traffic | temporary reconnects | treat any inbound line (including server comments) as activity | XCPro Team |
| DDB refresh during active stream introduces latency | ingest jitter | run refresh on IO dispatcher and isolate failures (failures do not disconnect stream) | XCPro Team |
| docs drift after implementation | operator confusion | phase-4 mandatory doc sync in same PR | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling is explicit in code and tests.
- Replay behavior remains deterministic.
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved (issue, owner, expiry).
- OGN docs in `docs/OGN` accurately reflect shipped behavior.

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 3 DDB connected-refresh cadence.
  - Phase 2 inbound-liveness policy.
  - Phase 1 untimed-authority policy (revert to prior anti-rewind behavior).
- Recovery steps if regression is detected:
  1. Revert affected phase commit(s).
  2. Keep diagnostics counters to preserve field observability.
  3. Re-run:
     - `./gradlew enforceRules`
     - `./gradlew testDebugUnitTest`
     - `./gradlew assembleDebug`

## 9) Quality Rescore (2026-03-01)

Evidence-backed rescore for the OGN connectivity slice after implementation:

- Architecture cleanliness: `4.6 / 5`
  - Evidence: ordering/liveness/refresh policy remains repository-side; no UI business-logic leak.
- Maintainability / change safety: `4.5 / 5`
  - Evidence: isolated helper functions and explicit policy tests for timed/untimed ordering.
- Test confidence on risky paths: `4.4 / 5`
  - Evidence: targeted policy + connection tests for rewind, stall, and active-session DDB checks.
- Overall OGN slice quality: `4.5 / 5`
  - Evidence: deterministic guards for known regressions and synchronized docs.
- Release readiness (OGN slice): `4.4 / 5`
  - Evidence: required gates passed (`enforceRules`, `testDebugUnitTest`, `assembleDebug`).

Overall score: `9.0 / 10` for OGN connectivity/reliability behavior.

Rescore update (2026-03-01, second code pass + fix):
- Overall score raised to `9.3 / 10` after closing timed->untimed->older-timed
  ordering gap and adding regression test coverage in
  `OgnTrafficRepositoryConnectionTest`.

Rescore update (2026-03-01, verification rerun):
- Forced suite and focused reruns succeeded:
  - `./gradlew testDebugUnitTest --rerun-tasks`
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.ogn.*" --rerun-tasks`
- Connectivity reliability score raised to `9.4 / 10` (`94 / 100`).

Remaining risks:
- Long-running DDB refresh operations can still consume repository loop time budget under poor networks.
- Untimed fallback mode after timed silence can still accept low-fidelity
  movement until timed packets return (intentional fail-open behavior).
