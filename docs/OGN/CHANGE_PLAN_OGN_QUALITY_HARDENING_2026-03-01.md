# CHANGE_PLAN_OGN_QUALITY_HARDENING_2026-03-01.md

## Purpose

Define a phased, release-grade plan to raise OGN implementation quality from
`8.8/10` to `>=9.5/10` by closing the remaining correctness, resilience, and
performance gaps found in the 2026-03-01 comprehensive code pass.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN quality hardening and release uplift
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: TBD
- Status: Implemented (Phases 0/1/2/3/4/5 complete)

## 0A) Implementation Update (2026-03-01)

Implemented in code:
- Phase 1 complete:
  - Added per-target latest accepted timed-source timestamp authority in
    `OgnTrafficRepository` so delayed older timed frames are rejected even after
    untimed fallback commits.
  - Added regression coverage:
    - `timedThenUntimedFallback_thenOlderTimedFrame_keepsUntimedPosition`
      in `OgnTrafficRepositoryConnectionTest`.
- Phase 2 complete:
  - DDB refresh scheduling is now explicit outcome based:
    - success path keeps standard cadence
    - failure path retries on bounded `2..5 min` window
  - DDB refresh no longer treats transport/HTTP/parser failure as success.
  - Non-2xx DDB HTTP responses and empty/invalid payloads are treated as
    refresh failures.
- Phase 3 complete:
  - OGN overlays no longer call `initialize()` from render hot-path.
  - `MapOverlayManager` now initializes OGN traffic/thermal/trail overlays when
    creating runtime overlay instances (style lifecycle ownership).
  - Render behavior remains unchanged; this is performance-only hardening.
- Phase 4 complete:
  - OGN docs synced for source-time authority and trail vario fallback wording.
- Phase 5 complete:
  - Required checks passed:
    - `python scripts/arch_gate.py`
    - `./gradlew enforceRules`
    - `./gradlew testDebugUnitTest`
    - `./gradlew testDebugUnitTest --rerun-tasks`
    - `./gradlew assembleDebug`
  - Additional verification:
    - targeted OGN repository tests passed:
      - `OgnTrafficRepositoryConnectionTest`
      - `OgnTrafficRepositoryPolicyTest`
      - rerun with explicit task execution:
        `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.ogn.*" --rerun-tasks`

Performance follow-on plan status:
- `docs/OGN/CHANGE_PLAN_OGN_OVERLAY_RENDER_INIT_FASTPATH_2026-03-01.md`
  has now been implemented in code.

## 1) Scope

- Problem statement:
  - OGN correctness, reliability, and overlay render-init performance gaps from
    the 2026-03-01 pass are implemented.
  - This document is retained as the execution and evidence record for release review.
- Why now:
  - Current quality is release-ready; the remaining item is a bounded perf
    optimization candidate.
- In scope:
  - OGN repository source-time authority hardening.
  - DDB retry cadence hardening (success vs failure windows).
  - OGN overlay runtime initialization optimization.
  - OGN docs/test vectors synchronization and quality rescore.
- Out of scope:
  - OGN uplink/transmit.
  - New tactical/collision features.
  - Non-OGN traffic paths (ADS-B, sensors fusion) except shared map runtime hooks.
- User-visible impact:
  - Fewer rare marker jumps in mixed-timestamp streams.
  - Faster recovery of identity/type enrichment after transient DDB failures.
  - Lower OGN overlay overhead in busy traffic sessions.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| per-target latest accepted timed source timestamp | `OgnTrafficRepository` | internal per-target map | overlay/UI-side source-time authority |
| DDB refresh success/failure scheduling state | `OgnTrafficRepository` + `OgnDdbRepository` | internal repository state | viewmodel/UI timers |
| OGN overlay style-init state | `MapOverlayManager` + `OgnTrafficOverlay` runtime | internal runtime flags | duplicated style init flags in ViewModel |
| OGN quality counters and diagnostics | `OgnTrafficSnapshot` | state flow snapshot | ad-hoc UI-local counters |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt` (only if diagnostics expand)
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt` (if style-generation hook needed)
  - OGN tests in `feature/map/src/test/java/com/trust3/xcpro/ogn/*`
  - OGN/map overlay tests in `feature/map/src/test/java/com/trust3/xcpro/map/*`
  - `docs/OGN/*.md`
- Boundary risk:
  - Low if all policy remains repository/runtime UI and no domain/UI crossing is introduced.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| timed ordering anchor across untimed commits | implicit previous-commit field | explicit per-target timed-source authority map | prevent older timed frame acceptance after untimed fallback | repository policy/connection tests |
| DDB retry pacing after failure | single pre-attempt gate timestamp | explicit success/failure-aware retry policy | avoid hour-long stale windows after transient errors | DDB cadence tests |
| overlay style re-init checks | every render path | style-change driven initialization | reduce per-frame overhead | overlay manager tests + manual perf smoke |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `handleIncomingLine` source ordering | compares only against previous committed source timestamp | compare against per-target latest accepted timed-source timestamp when present | Phase 1 |
| `refreshDdbIfDue` retry gate | attempt timestamp advanced before result | split success/failure retry windows with isolated failure counter | Phase 2 |
| `OgnTrafficOverlay.render` | re-enters `initialize()` on each render | initialize-on-style-ready and skip full checks when style generation unchanged | Phase 3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| APRS source timestamp (`sourceTimestampWallMs`) | Wall | packet event-time ordering authority |
| target receive timestamp (`lastSeenMillis`) | Monotonic | live ingest/order and staleness |
| timed-source silence fallback checks | Monotonic | session-local recency guard |
| DDB last successful refresh | Wall | external cache age/refresh contract |
| DDB failure retry backoff scheduling | Monotonic | robust transient retry pacing without wall-jump sensitivity |
| overlay redraw throttle timestamps | Monotonic | UI render cadence control |

Explicitly forbidden comparisons:

- Monotonic vs wall direct subtraction.
- Replay vs wall direct subtraction.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - OGN ingest/repository logic: injected IO dispatcher.
  - Overlay runtime: map/UI thread via map manager path.
- Primary cadence/gating sensor:
  - APRS line ingress for repository state.
  - display mode interval for overlay renders.
- Hot-path latency budget:
  - per-frame repository policy checks remain sub-millisecond average.
  - no network blocking inside APRS ingest loop.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No new randomness in ingest policy.
- Replay/live divergence rules:
  - OGN path remains live-only.
  - replay pipeline untouched.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| older timed frames accepted after untimed fallback | Architecture determinism + OGN ordering contract | unit/integration tests | `OgnTrafficRepositoryPolicyTest`, `OgnTrafficRepositoryConnectionTest` |
| stale DDB after transient failure | OGN metadata freshness contract | repository tests | `OgnTrafficRepositoryConnectionTest` (new DDB retry cases) |
| render-path overhead regressions | UI runtime safety/perf review | tests + review + manual smoke | map overlay tests and manual map stress run |
| docs/code timing drift | docs sync rule | review | `docs/OGN/OGN.md`, `docs/OGN/OGN_PROTOCOL_NOTES.md` |

## 3) Data Flow (Before -> After)

Before:

`APRS line -> parse -> source checks (previous commit only) -> commit`

`DDB due-check -> pre-attempt gate set -> refresh attempt (success/fail share same next-check wait)`

`Overlay render -> initialize checks -> feature build -> setGeoJson`

After:

`APRS line -> parse -> timed-authority checks (latest timed source + fallback policy) -> commit`

`DDB due-check -> result-aware scheduler (success window + fast failure retry window) -> refresh`

`Overlay render -> lightweight fast path when style already initialized for current style generation -> feature build -> setGeoJson`

## 4) Implementation Phases

### Phase 0 - Baseline lock and gap reproduction

- Goal:
  - Lock current behavior and add failing/expectation tests for all four gaps.
- Files to change:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/*Ogn*Overlay*Test.kt` (if needed)
- Tests to add/update:
  - timed->untimed->older-timed edge sequence.
  - DDB failure then retry timing behavior.
  - overlay render fast-path init expectations.
- Exit criteria:
  - deterministic tests reproduce pre-fix behavior.

### Phase 1 - Timed-source authority hardening

- Goal:
  - Enforce monotonic timed-source authority even after untimed fallback commits.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryPolicyTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
- Tests to add/update:
  - reject older timed frame when latest accepted timed source is newer.
  - keep untimed fallback behavior when timed is absent beyond fallback window.
- Exit criteria:
  - no timed source rewind across mixed timestamp modes.

### Phase 2 - DDB success/failure retry policy hardening

- Goal:
  - keep normal cadence on success; retry quickly after failure.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnDdbRepository.kt` (if contract expansion needed)
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
- Tests to add/update:
  - failure triggers retry within short window (2-5 min policy window).
  - success returns to standard due-check cadence.
  - refresh failure does not disconnect APRS stream.
- Exit criteria:
  - metadata stale window after transient failure reduced from ~1h to minutes.

### Phase 3 - Overlay initialization fast-path

- Goal:
  - avoid full style image/layer checks on every OGN render.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt` (if style generation tokening required)
  - map overlay tests under `feature/map/src/test/java/com/trust3/xcpro/map/`
- Tests to add/update:
  - repeated render calls do not trigger duplicate style initialization work.
  - map style change still reinitializes correctly.
- Exit criteria:
  - rendering correctness unchanged, initialization overhead reduced.

### Phase 4 - Docs and terminology sync

- Goal:
  - remove code/docs drift and clarify timebase terms.
- Files to change:
  - `docs/OGN/OGN.md`
  - `docs/OGN/OGN_PROTOCOL_NOTES.md`
  - `docs/OGN/OGN_APRS_TEST_VECTORS.md`
  - OGN repository comments/variable names where ambiguity exists
- Tests to add/update:
  - test names and vector notes aligned to semantics.
- Exit criteria:
  - docs accurately describe shipped behavior and timing contracts.

### Phase 5 - Release verification and rescore

- Goal:
  - certify release readiness for OGN slice.
- Files to change:
  - this plan status + rescore section.
  - optional release notes.
- Tests to add/update:
  - none expected beyond final pass/fixups.
- Exit criteria:
  - required checks pass and OGN score reaches target.

## 5) Test Plan

- Unit tests:
  - `OgnTrafficRepositoryPolicyTest`
  - `OgnTrafficRepositoryConnectionTest`
  - `OgnAprsLineParserTest`
  - OGN overlay/runtime tests in map package
- Replay/regression tests:
  - N/A for live OGN ingest; deterministic unit tests required.
- UI/instrumentation tests (if needed):
  - optional map style-change + OGN overlay instrumentation smoke.
- Degraded/failure-mode tests:
  - mixed timestamp mode regressions.
  - DDB transient failure retry behavior.
  - inbound stream stall with keepalive writes.
- Boundary tests for removed bypasses:
  - verify previous-commit-only source ordering bypass is closed.
  - verify pre-attempt DDB gate no longer delays retry by 1 hour on failure.

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
| strict timed-source authority rejects valid late frames | lower update rate in noisy feeds | keep fallback policy explicit and test real vectors | XCPro Team |
| aggressive DDB retries increase network load | battery/data impact | bounded backoff window with jitter and cap | XCPro Team |
| overlay optimization breaks style reinit after map style change | missing icons/layers | add style-change tests and manual map-style smoke | XCPro Team |
| docs drift after implementation | operator confusion | phase-4 mandatory docs sync in same change set | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling explicit in code and tests.
- Replay behavior remains deterministic.
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved.
- OGN score target reached with evidence:
  - Architecture cleanliness `>=4.5/5`
  - Maintainability `>=4.5/5`
  - Test confidence `>=4.5/5`
  - Release readiness `>=4.5/5`
  - Overall `>=9.5/10`

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 3 overlay fast-path changes.
  - Phase 2 DDB retry policy.
  - Phase 1 timed-source authority map.
- Recovery steps if regression is detected:
  1. Revert affected phase commit(s).
  2. Preserve diagnostics counters and logs where possible.
  3. Re-run:
     - `./gradlew enforceRules`
     - `./gradlew testDebugUnitTest`
     - `./gradlew assembleDebug`

## 9) Quality Rescore (2026-03-01)

- Architecture cleanliness: `4.7 / 5`
- Maintainability / change safety: `4.7 / 5`
- Test confidence on risky paths: `4.8 / 5`
- Overall OGN slice quality: `4.8 / 5`
- Release readiness (OGN slice): `4.8 / 5`

Overall score: `9.5 / 10` (`95 / 100`)

Remaining risks:
- Untimed fallback is intentionally fail-open after timed silence; low-fidelity
  movement may still be accepted until timed packets resume.
