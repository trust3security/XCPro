# OGN reconnect/runtime hardening change plan

## 0) Metadata

- Title: OGN reconnect/runtime hardening
- Owner: Codex
- Date: 2026-03-29
- Issue/PR: TBD
- Status: Draft

## 1) Scope

- Problem statement: OGN can silently churn on clean EOF, lacks explicit offline waiting, and does not serialize runtime state ownership cleanly.
- Why now: The current reconnect seam hides a real degraded state and makes future policy changes harder to reason about.
- In scope:
  - Fix unexpected clean stream termination handling.
  - Add explicit offline wait / resume behavior.
  - Serialize OGN runtime state ownership safely.
  - Improve OGN telemetry and regression tests.
- Out of scope:
  - Rewriting OGN into non-blocking sockets.
  - Major UI redesign.
  - Re-architecting ADS-B.
- User-visible impact:
  - OGN loss states become visible and less confusing.
  - OGN stops blind reconnect churn while offline.
- Rule class touched: Default + Guideline, with runtime behavior sensitivity.

## 2) Architecture contract

### 2.1 SSOT ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN connection lifecycle | `OgnTrafficRepositoryRuntime` | `OgnTrafficSnapshot.connectionState` | UI-owned reconnection state |
| OGN connection issue/reason | `OgnTrafficRepositoryRuntime` | `OgnTrafficSnapshot` field(s) | UI-only inferred reasons |
| OGN reconnect delay | `OgnTrafficRepositoryRuntime` | `OgnTrafficSnapshot.reconnectBackoffMs` | separate retry timers in UI |
| OGN network wait status | `OgnTrafficRepositoryRuntime` via injected port | `OgnTrafficSnapshot` field(s) | direct Android network calls from UI |
| OGN targets / suppression | `OgnTrafficRepositoryRuntime` | `targets`, `suppressedTargetIds`, snapshot | duplicate mutable mirrors outside repository |

### 2.1A State contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `connectionState` | OGN runtime | connection loop / reducer | snapshot -> map UI | runtime transport lifecycle | none | stop/disable | n/a | EOF/offline/error tests |
| `connectionIssue` or equivalent | OGN runtime | connection loop / reducer | snapshot -> UI/debug | exit reason / failures | none | clear on fresh connect | n/a | reason mapping tests |
| `reconnectBackoffMs` | OGN runtime | reconnect policy | snapshot | reconnect policy | none | clear on intentional reconnect or connected idle | monotonic duration | repeated EOF tests |
| `lastReconnectWallMs` | OGN runtime | reconnect policy | snapshot | clock | none | clear only if contract requires | wall | backoff/offline tests |
| network availability state | injected port + runtime wait seam | runtime wait helper | snapshot | port signal | none | runtime disabled | current state + monotonic waiting | offline resume tests |

### 2.2 Dependency direction

Confirm dependency flow remains: `UI -> domain -> data`

- Modules/files touched: `feature/traffic` OGN runtime and map UI builder, plus optional DI wiring.
- Any boundary risk: adding direct Android connectivity usage inside OGN runtime would be a boundary violation. Use a port.

### 2.2A Reference pattern check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntimeNetworkWait.kt` | explicit offline waiting and resume | injected network port + pause-until-online seam | OGN uses socket stream, so waiting must wrap socket attempts and retry delays without forcing ADS-B's whole loop shape |
| `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt` | serialized runtime ownership | explicit writer ownership | do not put blocking socket `readLine()` on the same single writer lane |

### 2.2B Boundary moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Offline detection / wait | implicit socket failure path | explicit OGN runtime + network port seam | clearer degraded-state handling | offline unit tests |
| OGN reconnect reason semantics | implicit throwable/simple-state inference | explicit runtime reason contract | safer UI mapping and supportability | snapshot/UI tests |

### 2.2C Bypass removal plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `updateCenter()` direct mutation | caller thread mutates runtime state | reducer/single-writer event or serialized mutator | 3 |
| collector callbacks mutating shared fields | raw scope on injected dispatcher | serialized state owner | 3 |

### 2.2D File ownership plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntime.kt` | existing | runtime SSOT + entrypoints | already owns OGN runtime | keep constructor/state ownership centralized | maybe |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt` | existing | connection loop and reconnect policy | current canonical owner | do not move policy into UI | maybe |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt` | existing | snapshot/state contract | canonical OGN model owner | map package only typealiases these models | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt` | existing | UI mapping only | current OGN/ADS-B indicator mapping | do not embed transport policy elsewhere | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/domain/OgnNetworkAvailabilityPort.kt` | new | OGN domain port for network availability | mirrors ADS-B boundary pattern | runtime should not call Android APIs directly | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeNetworkWait.kt` | new | OGN wait-until-online helper | keeps connection file focused | separate seam from socket loop | no |
| `feature/traffic/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt` | existing | OGN connection behavior tests | current seam tests live here | keep behavioral tests with runtime | maybe |
| `feature/traffic/src/test/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorModelTest.kt` | new or existing | UI mapping lock tests | indicator semantics deserve direct tests | avoid indirect UI assertions via runtime tests | no |

### 2.2E Module and API surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `OgnNetworkAvailabilityPort` | OGN domain/data boundary | OGN runtime, DI, tests | internal/public as repo pattern requires | explicit offline seam | keep minimal |
| snapshot issue/reason field(s) | OGN models | map UI, tests | existing model surface | structured degraded-state mapping | stable once added |

### 2.2F Scope ownership and lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| OGN state owner scope/reducer | serialize authoritative state | single-writer lane | repository stop/disable | caller-owned scopes would duplicate ownership |
| OGN socket I/O coroutine | blocking stream producer | raw injected IO dispatcher | stop/disable or reconnect | must not block state reducer lane |

### 2.2H Canonical formula / policy owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| OGN reconnect backoff policy | `OgnTrafficRepositoryRuntimeConnectionPolicies.kt` | runtime snapshot/UI | connection policy belongs in repository | no |
| offline wait policy | `OgnTrafficRepositoryRuntimeNetworkWait.kt` | runtime loop | transport wait seam belongs beside runtime | no |

### 2.3 Time base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| retry delay / stall detection | monotonic | elapsed-time safety |
| `lastReconnectWallMs` | wall | user/debug display timestamp |
| DDB age | wall | cache age reporting |
| target staleness checks | monotonic | transport freshness |

Explicitly forbidden:
- monotonic vs wall comparisons
- new direct wall-clock calls in domain/runtime

### 2.4 Threading and cadence

- Dispatcher ownership:
  - blocking socket I/O: injected IO dispatcher
  - authoritative state mutation: serialized writer lane
- Primary cadence/gating sensor: socket inbound activity + network availability port
- Hot-path latency budget: no avoidable extra waits on center/radius policy reconnects

### 2.5 Replay determinism

- Deterministic for same input: Yes
- Randomness used: No new randomness
- Replay/live divergence rules: none added

### 2.5A Error and degraded-state contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| clean EOF / stream end | Degraded / Recoverable | OGN runtime | lost/failure visible while retrying | exponential backoff | repeated EOF tests |
| offline before connect | Unavailable | OGN runtime | offline/lost visible | wait until online | offline-at-start test |
| offline during retry delay | Unavailable | OGN runtime | stays degraded, no blind socket churn | resume on online | offline-during-backoff test |
| login unverified | User action / Unavailable | OGN runtime | visible loss/failure | retry policy as designed, but reason explicit | auth reason test |
| inbound stall timeout | Degraded / Recoverable | OGN runtime | visible loss/failure | retry with backoff | stall regression test |
| intentional center/radius reconnect | Recoverable internal policy | OGN runtime | ideally hidden or non-alarming | immediate reconnect | policy reconnect tests |

### 2.6 Enforcement coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| UI starts owning transport policy | layering | review + UI tests | indicator tests |
| blocking I/O serialized with state reducer | runtime ownership | review + targeted tests | phase 3 review/tests |
| retry backoff reset regression | reconnect policy | unit test | connection test |
| offline wait regressions | degraded-state explicitness | unit test | connection test |
| hidden degraded state | UI mapping | unit/UI test | indicator test |

## 3) Data flow (before -> after)

Before:
`Socket / timeout / EOF -> OgnTrafficRepositoryRuntime mutates shared fields from multiple contexts -> OgnTrafficSnapshot -> map UI`

After:
`Socket + network port -> OGN runtime connection policy -> serialized runtime state owner -> OgnTrafficSnapshot (+ issue/reason) -> map UI`

## 4) Implementation phases

### Phase 0
- Goal: baseline docs, ownership, scaffolding, and test inventory
- Files to change: change plan doc, optional test helpers only
- Tests to add/update: none or scaffolding only
- Exit criteria: no production behavior changes

### Phase 1
- Goal: unexpected EOF becomes visible and backs off correctly
- Files to change: OGN connection policy, optional model/UI test files
- Tests to add/update: repeated EOF, intentional reconnect unchanged
- Exit criteria: repeated EOF no longer sticks at 1 second and is visible as degraded

### Phase 2
- Goal: explicit OGN offline wait seam
- Files to change: network port, runtime wait helper, runtime constructor/tests
- Tests to add/update: offline start, offline during backoff, online resume
- Exit criteria: OGN pauses while offline instead of blind retrying

### Phase 3
- Goal: serialized OGN state ownership
- Files to change: runtime entrypoints/mutation flow, possibly file split
- Tests to add/update: contention / ordering / center-radius churn
- Exit criteria: no authoritative state mutation from unowned contexts

### Phase 4
- Goal: structured telemetry + regression hardening
- Files to change: models, UI mapping, tests, optional docs
- Tests to add/update: issue/reason mapping, UI semantics, recovery telemetry
- Exit criteria: supportable degraded-state contract with direct tests

## 5) Test plan

- Unit tests:
  - repeated EOF escalates backoff
  - policy reconnect still immediate
  - offline-at-start waits without socket churn
  - offline during backoff resumes on online
  - login unverified reason visible
  - stall timeout remains covered
- Replay/regression tests:
  - fake clock driven reconnect timing
- UI/instrumentation tests:
  - indicator model tests for OGN degraded states
- Boundary tests for removed bypasses:
  - center/radius updates while connection loop active

Required checks:
```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| over-copying ADS-B single-writer pattern into blocking OGN loop | responsiveness regression | keep socket I/O as event producer, not sole writer lane | Codex |
| adding too many new states too early | broad compile churn | land smallest safe semantic fix in phase 1 first | Codex |
| offline wait hidden behind generic errors | poor supportability | add explicit reason contract by phase 4 | Codex |

## 7) Acceptance gates

- No architecture rule violations.
- No duplicated SSOT ownership introduced.
- Time-base handling explicit in code and tests.
- Error/degraded-state behavior explicit and tested where changed.
- OGN loss no longer stays hidden on repeated clean EOF.
- Offline wait is explicit and tested.

## 8) Rollback plan

- Phase 1 can be reverted independently.
- Phase 2 network port + wait helper can be reverted independently.
- Phase 3 serialization work should be landed in small slices to keep rollback simple.
- Keep tests from earlier phases even if later phases roll back.
