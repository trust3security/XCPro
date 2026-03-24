# LiveFollow Task Clear Remove Path Change Plan

## 0) Metadata

- Title: LiveFollow pilot task clear/remove path
- Owner: Codex
- Date: 2026-03-23
- Issue/PR: TBD
- Status: In progress

## 1) Scope

- Problem statement:
  - Spectator task overlay rendering was already wired for task upsert, but pilot-side task removal still left stale task geometry on the watcher because `null` task state was skipped before transport.
- Why now:
  - The approved next slice requires task removal to clear the server-side task state and remove the viewer overlay cleanly.
- In scope:
  - Treat pilot-side `no task` as an explicit LiveFollow task-clear event.
  - Keep clear on the task transport path, separate from position upload cadence.
  - Make the server clear current task state for the active live session while preserving revision/dedupe safety.
  - Ensure live-read watch payload returns `task: null` after clear.
  - Reuse the existing watch/UI/map detach path so the viewer overlay disappears.
- Out of scope:
  - Multi-glider spectator mode.
  - UI redesign.
  - New telemetry fields.
  - Position cadence changes.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Pilot task presence/geometry | `TaskManagerCoordinator.taskSnapshotFlow` | `LiveFollowTaskSnapshotSource` | ViewModel/UI/session mirrors |
| Pilot upload timing | `LiveFollowSessionRepository` | gateway calls only | UI-triggered direct transport writes |
| Wire clear representation | `CurrentApiLiveFollowSessionGateway` | transport-local request JSON only | repo/UI copies |
| Server current task state | deployed LiveFollow backend session task revision | live-read `task` object or `null` | watcher-side sticky caches |
| Watched task render state | `LiveFollowWatchViewModel` projection | `LiveFollowMapRenderState` | map-local authoritative task state |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Pilot task snapshot / no-task state | task export adapter | task SSOT only | session repo -> gateway | `TaskManagerCoordinator.taskSnapshotFlow` | none | task removed/invalidated, app restart | none | session repo tests |
| Last uploaded task fingerprint including clear payload | current API session gateway | successful task upload/clear only | gateway internal only | serialized request JSON | none | new session, gateway reset | none | gateway dedupe tests |
| Server current task revision | LiveFollow backend | `/api/v1/task/upsert` only | live-read endpoints | clear marker or full task payload | backend DB | later upsert/clear, session end | wall/server persistence only | backend API tests |
| Watched task snapshot | current API direct watch source | successful watch poll only | watch repo -> VM -> map | live-read `task` field | backend | watch stop/session switch/server clear | wall-read only at transport boundary | direct watch tests |

### 2.2 Dependency Direction

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature:livefollow`
  - `feature:map` tests only
  - backend repo transport/persistence route
  - docs
- Boundary risk:
  - Do not route task clear through position uploads.
  - Do not move task authority out of the existing task SSOT or watch SSOT.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/CurrentApiLiveFollowSessionGateway.kt` | owns LiveFollow current-API request shape and dedupe state | keep wire-shape ownership transport-local | extend same endpoint with explicit clear payload |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/watch/CurrentApiDirectWatchTrafficSource.kt` | already clears watched task when read payload is null | reuse null-task watch behavior as the overlay-clear trigger | none |
| `C:\\Users\\Asus\\AndroidStudioProjects\\XCPro_Server\\app\\main.py` current `task_upsert` + `build_live_response` | same deployed endpoint/read path | preserve revision-based current-state ownership | represent clear as a dedicated current revision instead of adding a new route |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/LiveFollowSessionRepository.kt` | Existing | session-owned pilot task upload/clear orchestration | task transport timing already lives here | avoid ViewModel or task-source side effects | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/CurrentApiLiveFollowSessionGateway.kt` | Existing | current API upsert/clear wire mapping and dedupe | transport-local state already here | avoid repo/UI transport knowledge | No |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/transport/LiveFollowCurrentApiTaskMapper.kt` | Existing | request JSON mapping for task upsert/clear | closest owner for payload shape | keep JSON literals out of repo orchestration | No |
| `feature/livefollow/src/test/java/com/example/xcpro/livefollow/data/session/*.kt` | Existing | client regressions for clear orchestration | closest owner | n/a | No |
| `feature/livefollow/src/test/java/com/example/xcpro/livefollow/data/watch/CurrentApiDirectWatchTrafficSourceTest.kt` | Existing | watcher clear regression | closest owner | n/a | No |
| `C:\\Users\\Asus\\AndroidStudioProjects\\XCPro_Server\\app\\main.py` | Existing | backend current API task clear persistence/read behavior | current deployed server is implemented here | no separate server route/module exists yet | No |
| `C:\\Users\\Asus\\AndroidStudioProjects\\XCPro_Server\\app\\tests\\test_livefollow_api.py` | Existing | backend regression coverage | closest owner | n/a | No |
| `docs/ARCHITECTURE/PIPELINE.md` | Existing | pipeline ownership update | required by repo rules | authoritative pipeline doc | No |
| `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v2.md` | Existing | frozen contract update | request/read behavior changed | authoritative contract doc | No |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Task clear trigger | none | event-driven on task removal / no-task state |
| Task clear wire payload | wall-less request body | clear is stateful, not timestamped |
| Watch aircraft freshness | monotonic | unchanged local watch arbitration path |
| Server task revision timestamps | wall/server | backend persistence concern only |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - replay still blocks all LiveFollow side effects
  - watch clear continues to depend on read payload only

## 3) Data Flow

Before:

`TaskManagerCoordinator.taskSnapshotFlow -> LiveFollowTaskSnapshotSource -> LiveFollowSessionRepository -> null task skipped -> stale backend task remains -> watch live-read still returns task -> watched overlay remains`

After:

`TaskManagerCoordinator.taskSnapshotFlow -> LiveFollowTaskSnapshotSource -> LiveFollowSessionRepository -> CurrentApiLiveFollowSessionGateway -> POST /api/v1/task/upsert { clear_task: true } -> backend stores clear as current task revision -> GET /api/v1/live/{session|share} returns task: null -> CurrentApiDirectWatchTrafficSource -> WatchTrafficRepository -> LiveFollowWatchViewModel -> MapLiveFollowRuntimeLayer detaches watched task overlay`

## 4) Test Plan

- XCPro JVM tests:
  - session repo forwards clear when task becomes `null`
  - current API gateway emits explicit clear payload and dedupes repeated clear
  - direct watch source drops watched task when server read returns `task: null`
- Backend tests:
  - clear payload validation
  - clear response returns `cleared: true`
  - live reads return `task: null` after clear
  - re-adding a task after clear works and restores read payload

Required XCPro checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 5) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| clear request accidentally fires on position cadence | unnecessary network churn and unclear ownership | keep clear in `LiveFollowSessionRepository` task collector only | Codex |
| server deletes history instead of current state | re-add/dedupe regression | store explicit clear as current revision and treat it as `task: null` on reads | Codex |
| watcher still holds stale overlay after server clear | stale geometry remains on map | keep watch SSOT null-task propagation and cover it with direct-watch regression tests | Codex |
