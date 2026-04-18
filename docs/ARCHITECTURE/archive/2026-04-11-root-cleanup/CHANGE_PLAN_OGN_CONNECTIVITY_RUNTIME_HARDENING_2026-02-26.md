# CHANGE_PLAN_OGN_CONNECTIVITY_RUNTIME_HARDENING_2026-02-26.md

## Purpose

Refactor and harden OGN connectivity/runtime behavior to reduce false failure cycles,
stabilize map-surface behavior, and keep architecture boundaries explicit.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN connectivity and runtime hardening
- Owner: XCPro Team
- Date: 2026-02-26
- Issue/PR: TBD
- Status: Complete (2026-02-26)

## 1) Scope

- Problem statement:
  OGN traffic integration is functionally complete but still has reliability and maintainability risk:
  false stall reconnect cycles in low-traffic windows, cross-module network-permission lint drift,
  and uneven test coverage around connection state transitions.
- Why now:
  OGN is always-on during map flight mode. Reconnect churn and partial guard coverage directly affect
  pilot trust and battery/network behavior.
- In scope:
  - OGN repository connection-loop/state-machine hardening.
  - Explicit stream-activity semantics (ingress + keepalive).
  - OGN connection/retry test expansion.
  - Module/app manifest permission alignment for network availability APIs.
  - OGN docs sync and cleanup for current runtime behavior.
- Out of scope:
  - OGN uplink/transmit implementation.
  - New protocol provider/back-end migration.
  - Task/replay domain behavior changes.
- User-visible impact:
  - Fewer spurious OGN error/reconnect cycles in sparse traffic areas.
  - More stable map traffic UX under variable network conditions.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN targets/snapshot state | `OgnTrafficRepository` | `StateFlow<List<OgnTrafficTarget>>`, `StateFlow<OgnTrafficSnapshot>` | UI-local authoritative traffic state |
| OGN settings (radius, auto radius, display mode, IDs) | `OgnTrafficPreferencesRepository` | preference-backed `Flow` | ViewModel/panel-local persisted mirrors |
| OGN thermal hotspots | `OgnThermalRepository` | `StateFlow<List<OgnThermalHotspot>>` | Overlay-owned thermal truth |
| OGN trail segments | `OgnGliderTrailRepository` | `StateFlow<List<OgnGliderTrailSegment>>` | Overlay-owned trail truth |
| OGN render throttle scheduling | `MapOverlayManager` runtime only | internal runtime state | Repository-side cadence throttles |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/*`
  - `feature/map/src/main/java/com/trust3/xcpro/map/*`
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/data/*` (lint-boundary alignment only)
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/*`
  - `feature/map/src/test/java/com/trust3/xcpro/map/*`
  - `app/src/main/AndroidManifest.xml`
  - `feature/map/src/main/AndroidManifest.xml`
  - `docs/OGN/*`
- Boundary risk:
  - leaking retry/policy logic into UI/runtime classes.
  - mixing render cadence policy with ingest/repository cadence.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN stream-activity definition | implicit read-line activity only | `OgnTrafficRepository` explicit ingress + keepalive activity | avoid false stall reconnects | repository connection tests |
| Network-permission contract for connectivity APIs | implicit app-only declaration | app + feature manifest declaration | remove module-lint blockers | `:feature:map:lintDebug` |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | N/A | N/A | N/A |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| OGN stall timeout, keepalive interval, stale sweep, reconnect backoff | Monotonic | duration math correctness and clock drift resistance |
| OGN DDB refresh interval timestamp | Wall | persistence/cache age semantics |
| Snapshot `lastReconnectWallMs`, UI age labels | Wall | user-facing diagnostics only |
| OGN auto-radius dwell/cooldown gates | Monotonic | deterministic transition timing |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - OGN socket and DDB I/O on injected IO dispatcher.
  - map overlay render scheduling on UI/runtime scope.
- Primary cadence/gating sensor:
  - ingest: APRS stream + repository connection loop.
  - render: UI display mode throttle only.
- Hot-path latency budget:
  - policy reconnect path: immediate (no backoff delay).
  - initial reconnect backoff start: `1_000 ms`.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No (except one-time generated client callsign persisted as config).
- Replay/live divergence rules:
  - OGN connectivity is live-only network path and does not alter replay fusion timing semantics.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| False OGN stall disconnects on quiet stream | ARCHITECTURE 10, CODING_RULES 12 | unit/integration repository tests | `OgnTrafficRepositoryConnectionTest` |
| Policy reconnect accidentally delayed by backoff | PIPELINE OGN reconnect contract | repository tests + review | `OgnTrafficRepositoryConnectionTest` |
| Manifest/permission drift for connectivity APIs | CONTRIBUTING + lint policy | lint | `:feature:map:lintDebug` |
| Render throttle leaks into ingest | SSOT/UDF boundaries | review + docs sync | `MapOverlayManager`, `PIPELINE.md`, `docs/OGN/*` |
| Regression from utility/test visibility changes | maintainability/rules | unit tests | `OgnGliderTrailOverlayRenderPolicyTest` |

## 3) Data Flow (Before -> After)

Before:

`OGN socket ingress -> repository state -> map runtime render`

Issue:
- quiet APRS windows could be treated as stalled despite healthy keepalive path.

After:

`OGN socket ingress + keepalive activity -> repository state machine -> map runtime render (mode-throttled only)`

No change to authoritative ownership:

`Source -> Repository (SSOT) -> UseCase -> ViewModel -> UI`

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  capture baseline failures and verify rule gates.
- Files to change:
  - none (evidence only).
- Tests to add/update:
  - none.
- Exit criteria:
  - baseline command matrix recorded.

### Phase 1 - Connection-loop hardening

- Goal:
  harden OGN stream activity and reconnect behavior.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt`
- Tests to add/update:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
- Exit criteria:
  - low-traffic quiet periods no longer trigger false stall errors.
  - policy reconnect remains immediate.

### Phase 2 - Runtime/test safety cleanup

- Goal:
  remove brittle test access patterns and enforce render-policy helpers cleanly.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnGliderTrailOverlay.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/OgnGliderTrailOverlayRenderPolicyTest.kt`
- Tests to add/update:
  - direct policy tests for render-cap helper.
- Exit criteria:
  - no reflection-dependent failure path.

### Phase 3 - Permission/lint boundary hardening

- Goal:
  clear module lint blockers for network availability APIs.
- Files to change:
  - `app/src/main/AndroidManifest.xml`
  - `feature/map/src/main/AndroidManifest.xml`
- Tests to add/update:
  - lint gate execution.
- Exit criteria:
  - `:feature:map:lintDebug` passes with zero errors.

### Phase 4 - Documentation and pipeline sync

- Goal:
  sync OGN docs with implemented behavior and remove stale assumptions.
- Files to change:
  - `docs/OGN/OGN.md`
  - `docs/OGN/OGN_PROTOCOL_NOTES.md`
  - `docs/ARCHITECTURE/PIPELINE.md` (if flow semantics changed)
- Tests to add/update:
  - N/A (docs).
- Exit criteria:
  - no mismatch between code and OGN protocol/runtime notes.

### Phase 5 - Final verification and quality rescore

- Goal:
  run required gates and record residual risk.
- Files to change:
  - this plan status + evidence.
- Tests to add/update:
  - N/A.
- Exit criteria:
  - required command matrix passes.

## 5) Test Plan

- Unit tests:
  - OGN connection harness tests (login filter/radius/reconnect semantics).
  - OGN trail render-policy unit tests.
- Replay/regression tests:
  - N/A for OGN network ingest path; replay unaffected.
- UI/instrumentation tests (if needed):
  - map traffic debug-panel behavior and overlay visibility edge cases.
- Degraded/failure-mode tests:
  - quiet stream with keepalive activity.
  - policy reconnect center/radius changes.
  - network callback registration failure fail-open behavior.
- Boundary tests for removed bypasses:
  - N/A.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Additional required for this slice:

```bash
./gradlew :feature:map:compileDebugKotlin
./gradlew :feature:map:testDebugUnitTest
./gradlew :feature:map:lintDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Over-hardening reconnect logic masks real socket failures | Medium | keep explicit stall timeout; count only successful keepalive writes | XCPro Team |
| Permission declarations diverge again between modules | Medium | enforce module lint in CI for map slice | XCPro Team |
| OGN docs become stale as behavior evolves | Medium | docs update in same PR for behavior changes | XCPro Team |
| Large warning count hides future regressions | Medium | follow-up warning burn-down plan after blocker closure | XCPro Team |

## 7) Acceptance Gates

- No architecture/coding-rule violations.
- No duplicate SSOT ownership introduced.
- OGN low-traffic quiet windows do not trigger false stall reconnect loops.
- `:feature:map:lintDebug` has zero errors.
- `./gradlew enforceRules`, `./gradlew testDebugUnitTest`, and `./gradlew assembleDebug` pass.
- `KNOWN_DEVIATIONS.md` remains unchanged unless explicitly approved deviation is required.

## 8) Rollback Plan

- What can be reverted independently:
  - OGN stream-activity logic changes in repository loop.
  - manifest permission additions.
  - trail-policy test accessibility cleanup.
  - docs sync edits.
- Recovery steps if regression is detected:
  1. Revert only the failing phase slice.
  2. Re-run targeted map module tests and lint.
  3. Re-apply fix in smaller patch with additional regression test.

## 9) Implementation Update (2026-02-26)

Implemented:

1. Hardened OGN stream-activity semantics in repository loop:
   - successful keepalive writes are counted as stream activity
   - quiet traffic windows no longer trigger false stall reconnect cycles
2. Stabilized OGN trail render-policy test access:
   - companion visibility aligned for module test calls
   - removed brittle reflection path from render-policy test
3. Resolved map-module lint blockers for network availability API:
   - added `ACCESS_NETWORK_STATE` permission declarations in app and feature map manifests
4. Synced OGN docs with actual runtime behavior:
   - keepalive activity semantics
   - connectivity/runtime notes parity

Verification matrix:

- `./gradlew :feature:map:compileDebugKotlin --no-configuration-cache --no-daemon` -> PASS
- `./gradlew :feature:map:testDebugUnitTest --no-configuration-cache --no-daemon` -> PASS
- `./gradlew :feature:map:lintDebug --no-configuration-cache --no-daemon` -> PASS
- `./gradlew enforceRules testDebugUnitTest assembleDebug --no-configuration-cache --no-daemon` -> PASS
