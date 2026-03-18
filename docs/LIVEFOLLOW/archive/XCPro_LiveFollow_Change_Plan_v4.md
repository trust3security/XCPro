# XCPro LiveFollow Change Plan v4

Date: 2026-03-17
Owner: draft for XCPro team
Status: Draft
Target repo: trust3security/XCPro
Issue/PR: TBD

---

## 0) Scope

### Problem statement

XCPro needs a live-follow system so one pilot can start sharing an active task and another XCPro user can watch that pilot with accurate task geometry, source state, and honest stale/offline behavior.

### Why now

The repo has already cleaned up task/map boundaries, removed manager escape hatches, and documented strict SSOT/use-case/render ownership. If LiveFollow is added without a repo-native plan, it will likely reintroduce the same architecture drift that was just removed.

### In scope

- pilot start/stop live session from an active XCPro task,
- follower watch entry via session route / notification,
- typed aircraft identity and conservative source binding,
- XCPro direct live fallback path,
- OGN integration as public traffic source,
- explicit watch/source state machine,
- task render snapshot consumption,
- required pipeline/doc updates.

### Out of scope

- AAT scoring engine,
- public share links,
- replay uploads,
- OGN uplink relay,
- general spectator leaderboard,
- durable raw-track history by default.

### User-visible impact

- pilot can start/stop sharing,
- followers can open a dedicated LiveFollow screen,
- map can show task geometry + watched aircraft + source/stale state,
- direct session watch works even when ordinary public OGN overlay prefs are off.

---

## 1) Architecture Contract

### 1.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Pilot ownship flight state | Existing flight runtime / `FlightDataRepository` path | exported ownship snapshot seam | second ownship pipeline inside LiveFollow |
| Active task definition + render snapshot | existing task repository/coordinator owners via task use-case seam | `TaskRenderSnapshot` / task-facing use case flow | LiveFollow-owned task mirror or map-owned task truth |
| Public OGN traffic state | OGN repository / traffic facade | typed target/state flows | session lifecycle or task truth in OGN layer |
| Live session lifecycle, permissions, follows | XCPro backend | session DTOs / watch models | map-owned or OGN-owned session truth |
| Android local live session state | `LiveFollowSessionRepository` | `StateFlow` | ViewModel-persisted mutable copy |
| Watch source arbitration state | `WatchTrafficRepository` + domain use cases | `StateFlow<WatchRenderState>` | UI-local arbitration logic |
| Live watch trail state | LiveFollow watch repository slice | watch trail state flow | reuse of ordinary SCIA selected-aircraft trail prefs |

### 1.2 Dependency Direction

Required direction remains:

`UI -> domain -> data`

Modules/files touched:
- `feature:livefollow` (new)
- task-facing use-case exports / adapters
- traffic-facing watch facade / adapters
- app DI + navigation + FCM wiring
- backend service(s)
- `docs/ARCHITECTURE/PIPELINE.md`

Boundary risks:
- bypassing task use-case/render snapshot seam,
- binding watch mode to ordinary OGN overlay prefs,
- leaking raw manager/controller handles,
- putting source arbitration in UI/map runtime.

### 1.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Live session/watch state | none | `LiveFollowSessionRepository` + backend | new feature needs a single authoritative session owner | repository/unit tests + no VM persistence |
| Watch source arbitration | ad hoc candidate in UI/map (forbidden) | domain use cases + `WatchTrafficRepository` | confidence/hysteresis must be authoritative and testable | pure unit tests + VM render tests |
| Live watch trail ownership | ordinary OGN trail prefs/path (wrong owner) | LiveFollow watch repository slice | watch mode must not depend on SCIA selected-aircraft prefs | pipeline doc + integration tests |
| Pilot telemetry export for LiveFollow | none | `LiveOwnshipSnapshotSource` | avoid second ownship pipeline and keep DI seam explicit | adapter tests + replay blocking tests |

### 1.2B Bypass Removal / Avoidance Plan (Mandatory)

| Bypass Callsite | Current Bypass Risk | Planned Replacement | Phase |
|---|---|---|---|
| LiveFollow reading coordinator internals directly | reintroduces escape hatches already removed | consume `TaskRenderSnapshot` from task use-case seam | 0-1 |
| Map/Composable direct task/session queries | violates UDF/SSOT | ViewModel/use-case state only | 3 |
| Watch mode bound to `ognOverlayEnabled` or `showSciaEnabled` | session can disappear when overlay prefs are off | dedicated watch repository/facade | 2 |
| Trail reuse through selected OGN aircraft keys | wrong ownership and session-local reset behavior | dedicated LiveFollow watch trail state | 2-3 |

### 1.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| pilot upload freshness | Monotonic | live freshness must be deterministic |
| viewer stale/offline thresholds | Monotonic | avoid wall-clock jitter |
| backend event audit timestamps | Wall | storage/debug/human inspection |
| UI "last updated" label | Wall | human-readable output |
| replay simulation timestamps | Replay | preserve deterministic replay semantics |
| task geometry/progress calculations | Monotonic + SI positional inputs | no wall/replay mixing |

Explicitly forbidden:
- monotonic vs wall comparisons,
- replay vs wall comparisons,
- wall-time domain gating.

### 1.4 Threading and Cadence

Dispatcher ownership:
- UI rendering: `Main`
- source arbitration / confidence / progress math: `Default`
- network / persistence / FCM / websocket: `IO`

Primary cadence/gating sensor:
- pilot upload gating is driven by the exported ownship snapshot cadence and monotonic freshness.

Proposed hot-path budget:
- local source arbitration update: under 50 ms typical
- viewer render state propagation after fresh sample: under 250 ms typical
- end-to-end perceived update: target under 2 s in nominal network conditions

Initial cadence guidance:
- pilot direct upload: 2-5 s while active session is healthy
- DB/history persistence: bounded snapshots, not every tick
- stale threshold: about 15 s
- offline threshold: about 45-60 s

### 1.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - replay may surface snapshot-shaped data for local rendering/tests,
  - replay must never start sessions, upload data, or notify followers,
  - domain logic must enforce replay side-effect blocking, not only UI.

### 1.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| direct wall-time usage in arbitration/progress | `ARCHITECTURE.md` time-base rules / `CODING_RULES.md` timebase rules | enforceRules + unit tests | source arbitration tests with fake clock |
| mixed units in telemetry/task progress | `ARCHITECTURE.md` SI rules / `CODING_RULES.md` unit rules | enforceRules + unit tests | telemetry mapper/progress tests |
| raw manager/controller exposure | `KNOWN_DEVIATIONS.md` resolved task escape hatches | enforceRules + review | no new raw-handle APIs |
| business logic in UI/map runtime | `ARCHITECTURE.md` UI rules / `CODING_RULES.md` VM/UI rules | review + VM tests + lint | LiveFollow VM tests |
| watch mode tied to OGN overlay prefs | pipeline contract + feature tests | integration/VM tests | watch-mode tests with public overlay off |
| replay side effects | `AGENT.md` / architecture determinism rules | unit + integration tests | replay-block tests |
| ambiguous identity auto-bind | quality/uncertainty intent rules | pure unit tests | identity confidence tests |

---

## 2) Data Flow (Before -> After)

### Before

```text
Pilot ownship: existing flight pipeline only
Follower watch: no dedicated session/watch pipeline
OGN map overlays: public-traffic preference-driven render path
Task render: task use-case/render-sync path already established
```

### After

```text
Pilot sensors
  -> existing flight runtime / FlightDataRepository SSOT
  -> LiveOwnshipSnapshotSource
  -> Start/maintain live session use cases
  -> backend upload adapter

Task SSOT
  -> task-facing use case
  -> TaskRenderSnapshot
  -> LiveFollow ViewModel / map runtime render consumer

Follower session route / FCM
  -> LiveFollowSessionRepository
  -> WatchTrafficRepository
      -> OGN typed-target stream
      -> XCPro direct stream
  -> source arbitration use case
  -> LiveFollowViewModel
  -> LiveFollow UI / map runtime renderer
```

---

## 3) State Machines

### 3.1 Session state

```text
IDLE
  -> STARTING
  -> ACTIVE
  -> STOPPING
  -> STOPPED
  -> ERROR
```

### 3.2 Watch source state

```text
WAITING_FOR_SOURCE
  -> OGN_CONFIRMED
  -> DIRECT_CONFIRMED
  -> AMBIGUOUS_IDENTITY
  -> STALE
  -> OFFLINE
  -> STOPPED
```

### Transition rules

- use dwell/hysteresis when switching between OGN and DIRECT,
- ambiguous identity blocks task bind,
- stale/offline are explicit user-visible states,
- session stop overrides source state.

---

## 4) Implementation Phases

### Phase 0 - Baseline and docs
Goal:
- lock the architecture before coding.

Files to change:
- new change plan doc
- new/revised `livefollow` feature-intent doc
- `PIPELINE.md` draft delta
- tests that lock current task/map/OGN behavior where touched

Tests to add/update:
- baseline regression tests for task render snapshot consumption path
- baseline regression test showing ordinary OGN overlay prefs do not define LiveFollow semantics yet

Exit criteria:
- docs approved,
- current behavior locked,
- no functional changes yet.

### Phase 1 - Pure domain logic
Goal:
- implement confidence/hysteresis/source-state logic and typed identity policy.

Files to change:
- `domain/livefollow/*`
- typed identity/source arbitration models
- replay-block policy use case

Tests to add/update:
- identity confidence tests
- stale/offline transition tests with fake clock
- OGN vs direct arbitration tests
- replay side-effect block tests

Exit criteria:
- no Android imports,
- deterministic tests pass,
- state machines explicit in code/tests.

### Phase 2 - Repository / SSOT wiring
Goal:
- add proper owners and adapters.

Files to change:
- `data/livefollow/*`
- ownship snapshot adapter
- watch/session repositories
- DI modules
- backend client/realtime adapters

Tests to add/update:
- repository tests with fake ports/adapters
- no duplicate SSOT state tests
- watch mode independent of OGN overlay prefs tests

Exit criteria:
- Flow/StateFlow only,
- no ViewModel persistence,
- no raw manager/controller exposure.

### Phase 3 - ViewModel + UI wiring
Goal:
- add user-facing pilot and follower flows.

Files to change:
- `feature/livefollow/*`
- app navigation/FCM routing
- thin map-render consumer wiring
- task/session entry points in feature tasks/profile as needed

Tests to add/update:
- VM tests for watch-state rendering
- Compose/instrumentation tests for route/notification entry
- ambiguous target does not attach task test

Exit criteria:
- end-to-end works in debug,
- UI renders only prepared state,
- no business math in UI.

### Phase 4 - Hardening and doc sync
Goal:
- verify the slice is stable and repo-compliant.

Files to change:
- `PIPELINE.md`
- quality rescore / PR summary
- documentation touched by final wiring

Tests to add/update:
- replay regression,
- failure/degraded-state tests,
- latency and lifecycle smoke checks,
- manual flight-test checklist updates

Exit criteria:
- required checks green,
- pipeline docs updated,
- change plan updated if architecture moved,
- quality rescore included.

---

## 5) Test Plan

### Unit tests
- canonical typed identity normalization
- confidence scoring and ambiguity handling
- source arbitration hysteresis/dwell
- stale/offline transitions with fake clock
- replay side-effect blocking
- SI unit contract assertions for telemetry mapping

### Replay/regression tests
- same replay input produces same local states
- replay cannot trigger live session side effects

### UI/instrumentation tests
- notification opens correct watch route
- public OGN overlay off does not kill active watch session UI
- ambiguous identity shows safe degraded state

### Degraded/failure-mode tests
- no OGN + no direct data -> waiting/offline state
- direct data stale -> stale state
- session stopped -> stop state
- backend unavailable -> explicit error state

### Boundary tests for removed bypasses
- no direct task manager/coordinator calls from Composables
- task render comes via exported task use-case snapshot only
- no raw manager/controller handles surfaced by new use cases

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew connectedDebugAndroidTest
```

---

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| ordinary OGN overlay prefs accidentally gate watch mode | feature appears broken | dedicated watch repository/facade; explicit tests | XCPro team |
| direct session path becomes a second ownship pipeline | duplicated truth / drift | exported ownship snapshot seam only | XCPro team |
| identity ambiguity attaches wrong task | trust failure | typed identity only + explicit ambiguity state | XCPro team |
| map runtime becomes logic owner again | architecture regression | render-only contract + VM/use-case tests | XCPro team |
| raw-track retention/privacy drift | privacy issue | hot-state default only + opt-in durable history | XCPro team |
| replay causes real side effects | correctness regression | hard block in domain + repository wiring | XCPro team |

---

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic and side-effect free
- Task rendering consumes exported task render snapshots, not manager escape hatches
- Watch mode is independent from ordinary OGN overlay and SCIA trail prefs
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved

---

## 8) Rollback Plan

What can be reverted independently:
- LiveFollow UI module can be disabled behind route/feature gate
- direct live upload adapter can be reverted without affecting existing flight runtime
- watch traffic repository can be disabled while leaving ordinary OGN overlays untouched

Recovery steps if regression is detected:
1. disable LiveFollow entry route and FCM routing,
2. disable direct live upload use case,
3. retain existing task/map/OGN flows untouched,
4. revert pipeline delta if necessary.

---

## 9) Recommended PR slicing

1. docs-only PR:
   - change plan
   - `livefollow` intent doc
   - pipeline draft/update
2. domain-only PR:
   - identity/source-state models + tests
3. repository/DI PR:
   - snapshot source + session/watch repos
4. UI/route PR:
   - feature screen + VM + notification entry
5. hardening PR:
   - doc sync, replay/privacy checks, quality rescore

Keep each PR small enough for normal repo review.
