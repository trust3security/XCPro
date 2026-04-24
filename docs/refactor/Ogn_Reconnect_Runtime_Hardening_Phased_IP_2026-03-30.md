# OGN reconnect/runtime hardening

## 0) Metadata

- Title: OGN reconnect/runtime hardening
- Owner: Codex
- Date: 2026-03-30
- Issue/PR: TBD
- Status: Blocked on required root unit gate (`:feature:traffic:testDebugUnitTest` test-worker OOM)

## 1) Scope

- Problem statement:
  - OGN could churn forever on clean EOF at the minimum retry cadence, had no explicit offline wait seam, and mutated runtime state from multiple contexts.
- Why now:
  - The old reconnect path hid real transport loss and made the runtime harder to reason about and support.
- In scope:
  - unexpected EOF hardening
  - explicit offline wait / resume seam
  - serialized OGN runtime state ownership
  - structured OGN connection issue telemetry and regression tests
- Out of scope:
  - rewriting OGN to non-blocking sockets
  - major UI redesign
  - ADS-B architecture changes outside shared connectivity pattern reuse
- User-visible impact:
  - OGN loss states are visible and easier to distinguish in UI/debug surfaces
  - OGN waits for network recovery instead of blindly reconnecting while offline
- Rule class touched: Default + Guideline

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN connection lifecycle | `OgnTrafficRepositoryRuntime` | `OgnTrafficSnapshot.connectionState` | UI-owned reconnect state |
| OGN connection issue semantics | `OgnTrafficRepositoryRuntime` | `OgnTrafficSnapshot.connectionIssue` | UI-side error inference |
| OGN reconnect delay | `OgnTrafficRepositoryRuntime` | `OgnTrafficSnapshot.reconnectBackoffMs` | separate retry timers in UI |
| OGN network availability state | `OgnTrafficRepositoryRuntime` via injected port | `OgnTrafficSnapshot.networkOnline` | direct Android connectivity calls from UI/runtime policy code |
| OGN targets + suppression | `OgnTrafficRepositoryRuntime` | target/snapshot flows | duplicate mutable mirrors outside repository |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `connectionState` | OGN runtime | writer-lane runtime mutations | snapshot -> UI/debug | socket lifecycle + offline wait seam | none | disable/stop | n/a | EOF/offline/auth/stall tests |
| `connectionIssue` | OGN runtime | writer-lane runtime mutations | snapshot -> UI/debug | transport failure mapping | none | clear on successful connect or intentional reconnect | n/a | issue mapping tests |
| `reconnectBackoffMs` | OGN runtime | reconnect policy on writer lane | snapshot | reconnect policy | none | clear on connect, intentional reconnect, offline wait, stop | monotonic duration | EOF/offline tests |
| `lastReconnectWallMs` | OGN runtime | reconnect policy on writer lane | snapshot | injected clock | none | clear on stop/disable | wall | reconnect regression tests |
| `networkOnline` | OGN runtime + injected port | port collector + offline wait seam | snapshot | `OgnNetworkAvailabilityPort` | none | stop/disable keeps latest known value | current state | offline wait tests |

### 2.2 Dependency Direction

Confirm dependency flow remains: `UI -> domain -> data`

- Modules/files touched:
  - `feature:traffic` OGN runtime, OGN domain/data connectivity seam, and map traffic UI mapping
- Boundary risk:
  - OGN runtime must not call Android `ConnectivityManager` directly; the new offline seam stays behind a domain port and data adapter.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt` | explicit single-writer runtime ownership | writer-lane state mutation | OGN keeps blocking socket I/O off the writer lane |
| `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntimeNetworkWait.kt` | explicit offline wait / resume seam | injected network port + online wait helper | OGN wraps socket reconnect timing instead of HTTP polling |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Offline detection / wait | implicit socket failure path | explicit OGN runtime + network availability port | clearer degraded-state handling | offline wait tests |
| OGN connection issue semantics | coarse `lastError` string | explicit `connectionIssue` contract | supportable UI/debug mapping | runtime + UI tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `updateCenter()` direct mutation | caller-thread writes shared runtime state | writer-scope mutation path | implemented |
| collector-driven runtime writes | raw dispatcher collectors | single writer lane | implemented |
| socket loop shared writes | blocking loop mutates runtime fields directly | blocking loop emits work back to writer lane | implemented |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt` | existing | OGN snapshot and issue contract | canonical OGN model owner | UI should not own transport semantics | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepository.kt` | existing | OGN repository DI/runtime wiring | canonical repository owner | keep constructor wiring out of UI/use-case code | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntime.kt` | existing | runtime owner fields, writer/io scopes, entrypoints | runtime SSOT owner | keep ownership explicit in one runtime host | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt` | existing | reconnect loop and socket event production | canonical OGN transport policy owner | UI/use-case layers must not own reconnect policy | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeNetworkWait.kt` | new | offline wait / resume helper | keeps connection file focused | separate from snapshot/UI mapping | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeDomainPolicies.kt` | existing | snapshot publication + domain-side runtime policies | existing owner for runtime helpers | avoid pushing snapshot logic into UI | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/domain/OgnNetworkAvailabilityPort.kt` | new | OGN connectivity boundary contract | domain/data seam | runtime must not depend on Android APIs directly | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/data/AndroidOgnNetworkAvailabilityAdapter.kt` | new | Android connectivity adapter | Android-only ownership boundary | keep platform types out of runtime policy | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/ogn/data/OgnNetworkAvailabilityTracker.kt` | new | adapter-local online state tracker | small focused helper | avoid bloating adapter class | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/di/TrafficBindingsModule.kt` | existing | Hilt binding for the new OGN port | canonical traffic DI module | do not hide bindings in runtime class | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt` | existing | read-only mapping from snapshot semantics to indicator UI | current indicator owner | UI mapping belongs here, not in repository | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficDebugPanelsSupport.kt` | existing | OGN debug labels | debug UI helper owner | keep label mapping out of repository | no |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/ui/MapTrafficDebugPanelsOgn.kt` | existing | debug panel rendering | UI-only owner | no business policy here | no |
| `feature/traffic/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTestSupport.kt` | existing | OGN runtime test helpers | canonical OGN connection test support | keep fake ports/sockets out of production code | no |
| `feature/traffic/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryReconnectHardeningTest.kt` | new | OGN reconnect/offline regression tests | focused regression owner | keep new behavior coverage small and explicit | no |
| `feature/traffic/src/test/java/com/trust3/xcpro/map/ui/MapTrafficConnectionIndicatorModelTest.kt` | existing | indicator semantics regression tests | direct UI mapping coverage | avoid proving UI via repository tests only | no |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `OgnNetworkAvailabilityPort` | OGN domain/data boundary | OGN runtime, DI, tests | public interface in module | explicit offline seam | stable narrow contract |
| `OgnTrafficSnapshot.connectionIssue` | OGN models | map UI/debug/tests | public data-model field | structured degraded-state mapping | stable once adopted |
| `OgnTrafficSnapshot.networkOnline` | OGN models | map UI/debug/tests | public data-model field | explicit offline visibility | stable once adopted |

### 2.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| OGN writer scope | serialize authoritative runtime mutation and snapshot publication | `ioDispatcher.limitedParallelism(1)` | repository lifetime | public callers/collectors must not mutate shared state directly |
| OGN I/O scope | blocking socket reads and DDB refresh event production | injected IO dispatcher | repository lifetime / loop cancellation | blocking socket I/O must not stall the writer lane |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| OGN reconnect backoff policy | `OgnTrafficRepositoryRuntimeConnectionPolicies.kt` | runtime snapshot/UI | reconnect policy belongs in repository transport policy | no |
| OGN offline wait policy | `OgnTrafficRepositoryRuntimeNetworkWait.kt` | runtime loop | offline gating belongs beside the runtime transport seam | no |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| retry delay / offline wait | Monotonic | reconnect timing must not depend on wall clock |
| target staleness sweeps | Monotonic | freshness policy stays deterministic |
| `lastReconnectWallMs` | Wall | debug/UI display timestamp only |
| DDB cache age | Wall | cache-age display only |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - blocking socket reads / DDB refresh: injected IO dispatcher
  - authoritative OGN state mutation + snapshot publication: writer lane
- Primary cadence/gating sensor:
  - socket inbound activity plus network availability flow
- Hot-path latency budget:
  - no extra blocking on the writer lane beyond state mutation and snapshot composition

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none added; this change affects live OGN transport runtime only

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| clean EOF / stream end | Degraded / Recoverable | OGN runtime | lost state visible | exponential backoff | reconnect hardening tests |
| offline before connect | Unavailable | OGN runtime | lost state visible, debug shows offline wait | wait for online | reconnect hardening tests |
| offline during backoff | Unavailable | OGN runtime | lost state stays visible, no socket churn | resume when online returns | reconnect hardening tests |
| login unverified | User action / Unavailable | OGN runtime | compact error indicator + structured debug reason | retry per existing reconnect policy | reconnect hardening tests + UI tests |
| inbound stall timeout | Degraded / Recoverable | OGN runtime | visible error state | retry with backoff | existing connection test |
| intentional center/radius reconnect | Recoverable internal policy | OGN runtime | no alarming lost indicator | immediate reconnect | existing connection test |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| UI starts inferring OGN transport policy | layering / SSOT | review + UI tests | `MapTrafficConnectionIndicatorModelTest.kt` |
| blocking socket I/O serialized with authoritative state | scope ownership / runtime ownership | review + runtime structure | `OgnTrafficRepositoryRuntime.kt` |
| repeated EOF resets backoff to minimum forever | reconnect policy | unit test | `OgnTrafficRepositoryReconnectHardeningTest.kt` |
| offline wait regresses into blind socket churn | degraded-state explicitness | unit test | `OgnTrafficRepositoryReconnectHardeningTest.kt` |
| structured OGN issues drift back to coarse strings only | explicit error semantics | unit test + UI tests | `OgnTrafficRepositoryReconnectHardeningTest.kt`, `MapTrafficConnectionIndicatorModelTest.kt` |

## 3) Data Flow (Before -> After)

Before:

`Socket / timeout / EOF -> OGN runtime shared-field mutation from multiple contexts -> OgnTrafficSnapshot -> map UI`

After:

`Socket + OgnNetworkAvailabilityPort -> connection/offline wait policy -> writer-lane OGN runtime mutation -> OgnTrafficSnapshot(connectionState, connectionIssue, networkOnline) -> map UI/debug`

## 4) Implementation Phases

1. Phase 0: baseline plan + touch-point inventory
2. Phase 1: unexpected EOF becomes visible and preserves backoff escalation
3. Phase 2: explicit offline wait seam through `OgnNetworkAvailabilityPort`
4. Phase 3: writer-lane runtime ownership with socket loop as event producer
5. Phase 4: structured issue telemetry and regression/UI hardening

## 5) Test Plan

- Unit tests:
  - repeated EOF backoff escalation
  - offline-at-start wait without socket churn
  - offline during backoff resumes on online
  - login unverified structured issue
  - indicator semantics for offline/auth OGN states
- Degraded/failure-mode tests:
  - existing stall timeout regression
- Boundary tests for removed bypasses:
  - existing center-move reconnect coverage

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| over-serializing blocking socket I/O | runtime responsiveness regression | keep socket reads on IO scope and route state mutation back to writer lane | Codex |
| local Windows/Gradle test instability | verification noise | use narrow targeted tests plus repo recovery steps when file locks/OOM hit | Codex |
| coarse UI wording masking real issues | support confusion | snapshot carries structured issue + debug panel label even when UI copy stays simple | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: Yes
- ADR:
  - `docs/ARCHITECTURE/ADR_OGN_RUNTIME_OFFLINE_WAIT_WRITER_LANE_2026-03-30.md`
- Decision summary:
  - OGN transport runtime uses an injected offline-wait seam and a split
    between the authoritative writer lane and the raw socket/DDB I/O lane.

## 7) Acceptance Gates

- No architecture rule violations
- No duplicate OGN SSOT ownership introduced
- Socket blocking I/O remains off the authoritative writer lane
- OGN degraded-state behavior is explicit and tested where changed
- OGN offline wait uses an injected boundary port, not Android APIs directly

## 8) Rollback Plan

- Revert `connectionIssue` / `networkOnline` model additions independently if needed
- Revert offline wait seam independently from writer-lane runtime ownership if a transport regression appears
- Keep regression tests from this work even if later runtime internals need adjustment

## 9) PR-Ready Checklist / Notes

### 9.1 Summary

- What changed:
  - OGN reconnect/runtime hardening now treats clean EOF as degraded transport
    loss, waits explicitly for network recovery through `OgnNetworkAvailabilityPort`,
    and keeps authoritative runtime mutation on a dedicated writer lane while
    blocking socket reads/DDB refresh stay on the IO lane.
- Why:
  - to stop blind offline reconnect churn, make degraded-state semantics
    supportable in UI/debug surfaces, and remove multi-context authoritative
    runtime mutation.
- Risk areas:
  - `feature:traffic` unit-test lane remains locally unstable because the
    Gradle test worker OOMs before a named assertion failure is reported.

### 9.2 Architecture Drift Checklist

- [x] MVVM + UDF + SSOT respected (no state duplication)
- [x] UI does not import `data` layer
- [x] Domain/use-cases do not import Android/UI types
- [x] ViewModels depend only on stable domain-facing seams (no platform APIs, no low-level persistence or infra types)
- [x] No raw manager/controller escape hatches exposed through use-cases or ViewModels
- [x] No Compose runtime state primitives used in non-UI managers/domain
- [x] No MapLibre types in domain/task managers
- [x] Timebase rules respected (no monotonic vs wall/replay mixing)
- [x] Replay remains deterministic for identical inputs
- [x] No known rule violation requires a new `KNOWN_DEVIATIONS.md` entry

### 9.3 Verification Record

Stabilization steps run before the required lane:

```bash
./gradlew --stop
./repair-build.bat all none
```

Required gate commands run:

```bash
./gradlew enforceRules --console=plain
./gradlew testDebugUnitTest --console=plain --stacktrace
./gradlew assembleDebug --console=plain
```

Supplemental diagnostics run after the root unit failure:

```bash
./gradlew :feature:traffic:testDebugUnitTest --console=plain --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryReconnectHardeningTest"
./gradlew :feature:traffic:testDebugUnitTest --console=plain --tests "com.trust3.xcpro.map.ui.MapTrafficConnectionIndicatorModelTest"
./gradlew :feature:traffic:testDebugUnitTest --console=plain --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryReconnectHardeningTest" --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryConnectionTest" --tests "com.trust3.xcpro.map.ui.MapTrafficConnectionIndicatorModelTest"
```

Results:

- [x] `./gradlew enforceRules --console=plain`
  - PASS
- [ ] `./gradlew testDebugUnitTest --console=plain --stacktrace`
  - FAIL
  - first failing task: `:feature:traffic:testDebugUnitTest`
  - first root-cause evidence:
    - `Exception: java.lang.OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "Test worker"`
    - `Execution failed for task ':feature:traffic:testDebugUnitTest'.`
    - `Process 'Gradle Test Executor 1' finished with non-zero exit value 1`
    - `org.gradle.internal.remote.internal.MessageIOException: Could not write '/127.0.0.1:55938'.`
    - `Caused by: java.io.IOException: Connection reset by peer`
  - interpretation:
    - current evidence points to a local Gradle/JVM test-worker memory failure
      inside the broad `feature:traffic` unit lane rather than a proven OGN
      reconnect assertion regression
- [x] `./gradlew assembleDebug --console=plain`
  - PASS

Supplemental diagnostic results:

- [x] `./gradlew :feature:traffic:testDebugUnitTest --console=plain --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryReconnectHardeningTest"`
  - PASS
- [x] `./gradlew :feature:traffic:testDebugUnitTest --console=plain --tests "com.trust3.xcpro.map.ui.MapTrafficConnectionIndicatorModelTest"`
  - PASS
- [ ] `./gradlew :feature:traffic:testDebugUnitTest --console=plain --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryReconnectHardeningTest" --tests "com.trust3.xcpro.ogn.OgnTrafficRepositoryConnectionTest" --tests "com.trust3.xcpro.map.ui.MapTrafficConnectionIndicatorModelTest"`
  - FAIL with the same `Test worker` OOM / connection-reset signature before a
    named assertion failure surfaced

Verification conclusion:

- The changed OGN reconnect hardening tests pass individually.
- The required root unit gate is still red because the broader
  `:feature:traffic:testDebugUnitTest` task crashes its Gradle test worker.
- Merge readiness remains blocked until the required root unit gate is green or
  the local worker issue is resolved and rerun.

### 9.4 Rollback Note

- One-step rollback command after merge:
  - `git revert <merge_commit_sha>`
- Post-rollback checks:
  - `./gradlew enforceRules --console=plain`
  - `./gradlew testDebugUnitTest --console=plain --stacktrace`
  - `./gradlew assembleDebug --console=plain`

### 9.5 Docs Checklist

- [x] `docs/ARCHITECTURE/PIPELINE.md` updated for the OGN offline-wait seam and
  writer-lane/IO-lane ownership split
- [x] ADR added:
  - `docs/ARCHITECTURE/ADR_OGN_RUNTIME_OFFLINE_WAIT_WRITER_LANE_2026-03-30.md`
- [x] `docs/ARCHITECTURE/ARCHITECTURE.md` update not required because repo-wide
  rules did not change
- [x] `docs/ARCHITECTURE/CODING_RULES.md` update not required because repo-wide
  rules did not change

## 10) Quality Rescore

- Architecture cleanliness: 4.5 / 5
  - Evidence:
    - injected connectivity seam in
      `feature/traffic/src/main/java/com/trust3/xcpro/ogn/domain/OgnNetworkAvailabilityPort.kt`
    - authoritative mutation stays in
      `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntime.kt`
    - pipeline/ADR docs now record the ownership decision
  - Remaining risks:
    - writer-lane discipline could drift later if future transport edits mutate
      shared runtime state from the IO lane again
- Maintainability / change safety: 4.0 / 5
  - Evidence:
    - offline-wait logic split into
      `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeNetworkWait.kt`
    - Android connectivity remains behind
      `feature/traffic/src/main/java/com/trust3/xcpro/ogn/data/AndroidOgnNetworkAvailabilityAdapter.kt`
    - ADR + change plan capture rollback and validation requirements
  - Remaining risks:
    - broad `feature:traffic` test-lane instability still makes final confidence
      slower to recover locally
- Test confidence on risky paths: 3.5 / 5
  - Evidence:
    - targeted reconnect hardening and indicator mapping tests pass
    - `./gradlew enforceRules --console=plain` and `./gradlew assembleDebug --console=plain` pass
  - Remaining risks:
    - required root `testDebugUnitTest` gate is not green
    - the module-level OOM prevents a full broad-suite claim
  - Why below 4.0:
    - merge-ready confidence is capped by the failing required root unit lane
- Overall map/task slice quality: 4.0 / 5
  - Evidence:
    - connection issue and `networkOnline` semantics are explicit end-to-end
      from repository snapshot to UI/debug mapping
  - Remaining risks:
    - no live runtime/device verification was rerun in this closeout pass
- Release readiness (map/task slice): 3.0 / 5
  - Evidence:
    - architecture/docs are in place and two of three required merge gates pass
  - Remaining risks:
    - blocked on the required root unit gate
  - Why below 4.0:
    - the raw repo-required `testDebugUnitTest` command is still failing

## 11) Merge Readiness

- Status:
  - blocked for merge
- Blocking condition:
  - required verification lane is not fully green because
    `./gradlew testDebugUnitTest --console=plain --stacktrace` fails first in
    `:feature:traffic:testDebugUnitTest` with a Gradle test-worker OOM
- Current best evidence:
  - not a proven reconnect/runtime regression in the changed OGN slice
  - likely local Gradle/JVM test-worker instability inside the broader
    `feature:traffic` unit lane
